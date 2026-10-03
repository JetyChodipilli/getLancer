package com.getlancer.hosting;

import com.getlancer.shared.ApiError;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Inspects bytes in memory only. Never extracts, builds, installs, or executes an archive. */
public final class StaticArchive {
  public static final int MAX_COMPRESSED=5*1024*1024, MAX_EXPANDED=10*1024*1024, MAX_FILES=256;
  public record File(String path,String sha256,int sizeBytes,byte[] content) {
    public File { content=content.clone(); }
    @Override public byte[] content() {return content.clone();}
    public Map<String,Object> manifest() {return Map.of("path",path,"sha256",sha256,"sizeBytes",sizeBytes);}
    public Map<String,Object> publication() {return Map.of("path",path,"sha256",sha256,"sizeBytes",sizeBytes,"contentBase64",Base64.getEncoder().encodeToString(content));}
  }
  public record Inspection(String archiveSha256,String manifestSha256,int sizeBytes,int expandedBytes,List<File> files) {
    public Inspection {files=List.copyOf(files);}
    public List<Map<String,Object>> manifest() {return files.stream().map(File::manifest).toList();}
  }
  private record Entry(String name,int size,long crc,int offset,int end) {}
  private static final Set<String> EXTENSIONS=Set.of("html","css","js","json","png","jpg","jpeg","webp","gif","svg","ico","woff","woff2","ttf","txt","webmanifest");
  private static final Pattern TOKEN_PATH=Pattern.compile("(?:^|[._-])tokens?(?:[._-]|$)",Pattern.CASE_INSENSITIVE);
  private static final Pattern PRIVATE_PATH=Pattern.compile("(?:^|[._-])(?:secrets?|credentials?|private|passwords?|api[_-]?keys?)(?:[._-]|$)",Pattern.CASE_INSENSITIVE);
  private static final Pattern SECRET_MARKER=Pattern.compile("(?is)-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----|\\bAKIA[A-Z0-9]{16}\\b|\\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}\\b|\\brzp_live_[A-Za-z0-9]{8,}\\b");
  private static final Pattern SECRET_ASSIGNMENT=Pattern.compile("(?:^|[\\s;{,])[\"']?([A-Za-z0-9_]{1,128})[\"']?[ \\t]*[:=][ \\t]*[\"']([A-Za-z0-9+/=_-]{16,})[\"']");
  private static boolean secret(String text){if(SECRET_MARKER.matcher(text).find())return true;var matches=SECRET_ASSIGNMENT.matcher(text);while(matches.find()){String key=matches.group(1).toUpperCase(Locale.ROOT);if(key.contains("SECRET")||key.contains("PRIVATE_KEY")||key.contains("PRIVATEKEY")||key.contains("PASSWORD")||key.contains("ACCESS_TOKEN")||key.contains("ACCESSTOKEN")||key.contains("API_KEY")||key.contains("APIKEY"))return true;}return false;}

  private static ApiError unsafe(String message) {return new ApiError(400,"UNSAFE_STATIC_ARCHIVE",message);}
  public static String sha(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
  static void path(String name,boolean directory) {
    if(name.isEmpty()||name.length()>240||!name.matches("[A-Za-z0-9._/-]+")||name.startsWith("/")||name.startsWith(".")||(!directory&&name.endsWith("/")))throw unsafe("Use safe ASCII relative static paths.");
    var parts=name.split("/",-1);
    for(int i=0;i<parts.length;i++)if(parts[i].startsWith(".")||parts[i].isEmpty()&&i<parts.length-1)throw unsafe("Hidden, empty, and traversal path segments are not accepted.");
    for(String part:parts)if(PRIVATE_PATH.matcher(part).find())throw unsafe("Private file names are not accepted.");
    for(int i=0;i<parts.length;i++)if(TOKEN_PATH.matcher(parts[i]).find()&&!(i==parts.length-1&&parts[i].toLowerCase(Locale.ROOT).endsWith(".css")))throw unsafe("Private token file names are not accepted; design-token CSS assets are allowed.");
    String lower=name.toLowerCase(Locale.ROOT),base=parts[parts.length-1].toLowerCase(Locale.ROOT);
    if(lower.matches(".*(?:credentials|secret-key|private-key|service-account|id_rsa|id_ed25519|node_modules|package-lock|yarn.lock|pnpm-lock).*")||base.equals("package.json"))throw unsafe("Remove private files, source manifests, credentials and dependencies.");
    if(!directory) {
      boolean notice=base.matches("(?:readme|licen[cs]e)(?:\\.(?:md|txt))?");
      String ext=base.contains(".")?base.substring(base.lastIndexOf('.')+1):"";
      if(!notice&&!EXTENSIONS.contains(ext))throw unsafe("Only built static assets and useful README/LICENSE notices are accepted.");
    }
  }
  public static Inspection inspect(byte[] bytes) {
    if(bytes.length<22||bytes.length>MAX_COMPRESSED)throw unsafe("Choose a ZIP up to 5 MiB.");
    try {
      var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);int end=-1;
      for(int i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)if(b.getInt(i)==0x06054b50&&i+22+u16(b,i+20)==bytes.length){end=i;break;}
      if(end<0||u16(b,end+4)!=0||u16(b,end+6)!=0)throw unsafe("Split, ZIP64 and incomplete archives are not supported.");
      int count=u16(b,end+10);long offset=u32(b,end+16),length=u32(b,end+12);
      if(count<1||count>512||u16(b,end+8)!=count||offset+length!=end||offset>Integer.MAX_VALUE)throw unsafe("Invalid or excessive ZIP directory.");
      int p=(int)offset,expanded=0,fileCount=0;var entries=new LinkedHashMap<String,Entry>();var folded=new HashSet<String>();var prefixes=new HashMap<String,String>();var ranges=new ArrayList<Entry>();
      for(int n=0;n<count;n++) {
        if(p+46>end||b.getInt(p)!=0x02014b50)throw unsafe("Invalid ZIP directory.");
        int flags=u16(b,p+8),method=u16(b,p+10),names=u16(b,p+28),extra=u16(b,p+30),comment=u16(b,p+32);long size=u32(b,p+24),compressed=u32(b,p+20),local=u32(b,p+42),attrs=u32(b,p+38);
        if((flags&~(8|2048))!=0||!Set.of(0,8).contains(method)||u16(b,p+6)>=45||size==0xffffffffL||compressed==0xffffffffL||local==0xffffffffL||p+46+names+extra+comment>end||u16(b,p+34)!=0)throw unsafe("Encrypted, ZIP64 or unsupported entries are not accepted.");
        extras(b,p+46+names,extra);String name=decode(Arrays.copyOfRange(bytes,p+46,p+46+names));boolean directory=name.endsWith("/");path(name,directory);
        int type=(int)(attrs>>>16)&0170000;
        if(type!=0&&type!=(directory?0040000:0100000)||(attrs&0x400)!=0)throw unsafe("Links and special files are not accepted.");
        String key=name.replaceAll("/$","").toLowerCase(Locale.ROOT);
        if(!folded.add(key)||entries.containsKey(name))throw unsafe("Duplicate or case-colliding paths are not accepted.");
        String cumulative="";for(String segment:name.replaceAll("/$","").split("/")){cumulative=cumulative.isEmpty()?segment:cumulative+"/"+segment;String prior=prefixes.putIfAbsent(cumulative.toLowerCase(Locale.ROOT),cumulative);if(prior!=null&&!prior.equals(cumulative))throw unsafe("Directory-prefix case collisions are not accepted.");}
        if(size>MAX_COMPRESSED||directory&&size!=0||(expanded+=Math.toIntExact(size))>MAX_EXPANDED||!directory&&++fileCount>MAX_FILES)throw unsafe("Use at most 256 files, 10 MiB expanded, and 5 MiB per file.");
        if(local+30>offset||b.getInt((int)local)!=0x04034b50)throw unsafe("Invalid ZIP local entry.");
        int localNames=u16(b,(int)local+26),localExtra=u16(b,(int)local+28);long data=local+30+localNames+localExtra,finish=data+compressed;
        if(finish>offset||u16(b,(int)local+4)>=45||u16(b,(int)local+8)!=method||u16(b,(int)local+6)!=flags||!name.equals(decode(Arrays.copyOfRange(bytes,(int)local+30,(int)local+30+localNames))))throw unsafe("ZIP headers disagree.");
        extras(b,(int)local+30+localNames,localExtra);
        if((flags&8)==0&&(u32(b,(int)local+14)!=u32(b,p+16)||u32(b,(int)local+18)!=compressed||u32(b,(int)local+22)!=size))throw unsafe("ZIP local sizes disagree.");
        var entry=new Entry(name,(int)size,u32(b,p+16),(int)local,(int)finish);entries.put(name,entry);ranges.add(entry);p+=46+names+extra+comment;
      }
      if(p!=end)throw unsafe("Invalid ZIP directory bounds.");
      ranges.sort(Comparator.comparingInt(Entry::offset));int lastEnd=0;for(var range:ranges){if(range.offset<lastEnd)throw unsafe("Overlapping ZIP entries are not accepted.");lastEnd=range.end;}
      for(String name:entries.keySet())if(!name.endsWith("/"))for(String other:entries.keySet())if(other.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT)+"/"))throw unsafe("A file cannot also be a directory.");
      var files=new ArrayList<File>();int actual=0,total=0;
      try(var stream=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
        ZipEntry z;while((z=stream.getNextEntry())!=null) {
          var expected=entries.remove(z.getName());if(expected==null)throw unsafe("Unexpected ZIP entry.");actual++;var data=new ByteArrayOutputStream();byte[] buf=new byte[8192];int read,entrySize=0;var crc=new CRC32();
          while((read=stream.read(buf))!=-1){entrySize+=read;total+=read;if(entrySize>expected.size||total>MAX_EXPANDED)throw unsafe("Expanded ZIP bounds exceeded.");data.write(buf,0,read);crc.update(buf,0,read);}
          if(entrySize!=expected.size||crc.getValue()!=expected.crc)throw unsafe("ZIP integrity failed.");
          if(!z.isDirectory()){byte[] file=data.toByteArray();inspectFile(z.getName(),file);files.add(new File(z.getName(),sha(file),file.length,file));}stream.closeEntry();
        }
      }
      if(!entries.isEmpty()||actual!=count||files.stream().noneMatch(f->f.path.equals("index.html")&&f.sizeBytes>0))throw unsafe("Include a nonempty root index.html in a complete standard ZIP.");
      files.sort(Comparator.comparing(File::path));var manifest=new StringBuilder();for(var f:files)manifest.append(f.path).append('\0').append(f.sha256).append('\0').append(f.sizeBytes).append('\n');
      return new Inspection(sha(bytes),sha(manifest.toString().getBytes(StandardCharsets.UTF_8)),bytes.length,total,files);
    }catch(ApiError e){throw e;}catch(Exception e){throw unsafe("This ZIP could not be safely inspected.");}
  }
  private static void inspectFile(String name,byte[] file) throws CharacterCodingException {
    if(dangerousMagic(file)||secret(new String(file,StandardCharsets.ISO_8859_1)))throw unsafe("Nested archives, executables and credentials are not accepted.");
    String base=name.substring(name.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
    if(base.matches(".*\\.(?:html|css|js|json|svg|txt|webmanifest)$")||base.matches("(?:readme|licen[cs]e)(?:\\.(?:md|txt))?")){String text=decode(file);if(text.indexOf('\0')>=0||secret(text))throw unsafe("Remove credentials or binary data from text assets.");if(base.matches("(?:readme|licen[cs]e)(?:\\.(?:md|txt))?")&&text.trim().length()<20)throw unsafe("README and LICENSE notices must contain useful text.");}
  }
  private static boolean dangerousMagic(byte[] bytes){
    for(String hex:List.of("4d5a","7f454c46","cafebabe","bebafeca","cffaedfe","cefaedfe","feedfacf","feedface","504b0304","504b0506","504b0708","1f8b","377abcaf271c","52617221","425a68","fd377a585a00")){byte[] magic=HexFormat.of().parseHex(hex);if(bytes.length>=magic.length){boolean same=true;for(int i=0;i<magic.length;i++)same&=bytes[i]==magic[i];if(same)return true;}}
    return bytes.length>=262&&new String(bytes,257,5,StandardCharsets.US_ASCII).equals("ustar");
  }
  private static void extras(ByteBuffer b,int start,int size){int end=start+size;while(start<end){if(start+4>end)throw unsafe("Invalid ZIP extra fields.");int tag=u16(b,start),len=u16(b,start+2);if(tag==1||start+4+len>end)throw unsafe("ZIP64 or invalid extra fields.");start+=4+len;}}
  private static int u16(ByteBuffer b,int p){return Short.toUnsignedInt(b.getShort(p));}
  private static long u32(ByteBuffer b,int p){return Integer.toUnsignedLong(b.getInt(p));}
  private static String decode(byte[] bytes) throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
}
