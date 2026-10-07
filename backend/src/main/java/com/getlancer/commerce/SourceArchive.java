package com.getlancer.commerce;

import com.getlancer.shared.ApiError;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Static bounded inspection only. Submitted source is never extracted or executed. */
public final class SourceArchive {
  public static final int MAX_COMPRESSED=5*1024*1024, MAX_EXPANDED=25*1024*1024, MAX_ENTRIES=500;
  public record Inspection(String sha256,int sizeBytes,int entryCount,List<String> manifestFiles) {}
  private record Entry(String name,long size,long crc) {}
  private static ApiError unsafe(String message) { return new ApiError(400,"UNSAFE_SOURCE_ARCHIVE",message); }
  private static final Pattern SECRET=Pattern.compile("(?is)-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----|\\bAKIA[A-Z0-9]{16}\\b|\\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}\\b|\\brzp_live_[A-Za-z0-9]{8,}\\b|(?m)^\\s*(?:export\\s+)?(?:(?:const|let|var|public|private|protected|static|final|String)\\s+)*[\"']?(?:[A-Z0-9_]*(?:SECRET|PRIVATE_?KEY|PASSWORD|ACCESS_?TOKEN|API_?KEY)[A-Z0-9_]*)[\"']?\\s*[:=]\\s*[\"']?(?![\"']?(?:$|\\$|<|your|example|change|placeholder|process\\.|env\\.|test|demo|false|true|null|undefined))[A-Za-z0-9+/=_-]{16,}");
  public static Inspection inspect(byte[] bytes) {
    if(bytes.length<22 || bytes.length>MAX_COMPRESSED) throw unsafe("Choose a valid ZIP archive up to 5 MiB.");
    try {
      ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      int end=-1;
      for(int i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--) if(b.getInt(i)==0x06054b50 && i+22+u16(b,i+20)==bytes.length) {end=i;break;}
      if(end<0 || u16(b,end+4)!=0 || u16(b,end+6)!=0) throw unsafe("Split or incomplete ZIP archives are not supported.");
      int count=u16(b,end+10);long cdSize=u32(b,end+12),cdOffset=u32(b,end+16);
      if(count<2 || count>MAX_ENTRIES || u16(b,end+8)!=count || cdOffset+cdSize!=end || cdOffset>Integer.MAX_VALUE) throw unsafe("Use 2–500 files and a standard ZIP archive.");
      Map<String,Entry> entries=new LinkedHashMap<>();Set<String> folded=new HashSet<>();long expanded=0;int p=(int)cdOffset;
      for(int n=0;n<count;n++) {
        if(p+46>end || b.getInt(p)!=0x02014b50) throw unsafe("Invalid ZIP directory.");
        int flags=u16(b,p+8),method=u16(b,p+10),nameLength=u16(b,p+28),extra=u16(b,p+30),comment=u16(b,p+32);
        long size=u32(b,p+24), compressed=u32(b,p+20), offset=u32(b,p+42),attrs=u32(b,p+38);
        if((flags & (1|64|8192))!=0 || !Set.of(0,8).contains(method) || size==0xffffffffL || compressed==0xffffffffL || offset==0xffffffffL || p+46+nameLength+extra+comment>end || u16(b,p+34)!=0) throw unsafe("Encrypted, ZIP64 or unsupported ZIP entries are not accepted.");
        String name=decode(Arrays.copyOfRange(bytes,p+46,p+46+nameLength)); path(name);
        int unixType=(int)((attrs>>>16)&0170000);
        if(unixType!=0 && unixType!=0100000 && unixType!=0040000) throw unsafe("Links and special files are not accepted.");
        if((attrs & 0x400)!=0) throw unsafe("Reparse points are not accepted.");
        if(!folded.add(name.toLowerCase(Locale.ROOT)) || entries.containsKey(name)) throw unsafe("Duplicate file paths are not accepted.");
        if((name.endsWith("/") && size!=0) || size>5*1024*1024 || (expanded+=size)>MAX_EXPANDED) throw unsafe("Expanded source must stay below 25 MiB, each file below 5 MiB, and directories must be empty.");
        if(offset+30>=cdOffset || b.getInt((int)offset)!=0x04034b50) throw unsafe("Invalid ZIP local entry.");
        int localName=u16(b,(int)offset+26),localExtra=u16(b,(int)offset+28);
        if(offset+30+localName+localExtra+compressed>cdOffset || !name.equals(decode(Arrays.copyOfRange(bytes,(int)offset+30,(int)offset+30+localName))) || u16(b,(int)offset+8)!=method || u16(b,(int)offset+6)!=flags) throw unsafe("ZIP local and directory entries disagree.");
        entries.put(name,new Entry(name,size,u32(b,p+16)));p+=46+nameLength+extra+comment;
      }
      if(p!=end) throw unsafe("Invalid ZIP directory bounds.");
      boolean readme=false,license=false;int actual=0;long total=0;List<String> manifests=new ArrayList<>();
      try(var stream=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
        ZipEntry z;
        while((z=stream.getNextEntry())!=null) {
          Entry expected=entries.remove(z.getName());if(expected==null) throw unsafe("Unexpected or duplicate ZIP entry.");
          actual++;var data=new ByteArrayOutputStream();byte[] buf=new byte[8192];int read;long entrySize=0;CRC32 crc=new CRC32();
          while((read=stream.read(buf))!=-1) {entrySize+=read;total+=read;if(entrySize>expected.size || total>MAX_EXPANDED) throw unsafe("Expanded ZIP bounds were exceeded.");data.write(buf,0,read);crc.update(buf,0,read);}
          if(entrySize!=expected.size || crc.getValue()!=expected.crc) throw unsafe("ZIP file integrity failed.");
          if(!z.isDirectory()) {
            String basename=z.getName().substring(z.getName().lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
            byte[] file=data.toByteArray();inspectFile(basename,file);
            if(basename.matches("readme(?:\\.(?:md|txt|rst))?") && entrySize>=20) readme=true;
            if(basename.matches("(?:license|licence)(?:\\.(?:md|txt))?") && entrySize>=20) license=true;
            if(Set.of("package.json","pom.xml","build.gradle","build.gradle.kts","pyproject.toml","requirements.txt","cargo.toml","go.mod","composer.json","gemfile","pubspec.yaml","mix.exs","makefile","cmakelists.txt").contains(basename) && entrySize>=2) manifests.add(z.getName());
          }
          stream.closeEntry();
        }
      }
      if(!entries.isEmpty() || actual!=count || !readme || !license || manifests.isEmpty()) throw unsafe("Include a useful README, LICENSE with third-party notices and a supported build manifest.");
      return new Inspection(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),bytes.length,count,List.copyOf(manifests));
    } catch(ApiError e) {throw e;} catch(Exception e) {throw unsafe("This ZIP archive could not be safely inspected.");}
  }
  private static int u16(ByteBuffer b,int p) {return Short.toUnsignedInt(b.getShort(p));}
  private static long u32(ByteBuffer b,int p) {return Integer.toUnsignedLong(b.getInt(p));}
  private static String decode(byte[] bytes) throws CharacterCodingException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
  private static void path(String name) {
    if(name.length()>240 || name.isBlank() || name.startsWith("/") || name.contains("\\") || !name.matches("[A-Za-z0-9_./ +@()-]+")) throw unsafe("Use safe relative source paths.");
    String[] parts=name.split("/",-1);for(int i=0;i<parts.length;i++) if(parts[i].equals(".") || parts[i].equals("..") || (parts[i].isEmpty() && i<parts.length-1)) throw unsafe("Unsafe ZIP path.");
    String lower=name.toLowerCase(Locale.ROOT);
    if(lower.matches("(?:.*/)?(?:\\.git|\\.svn|node_modules)(?:/.*)?") || lower.matches("(?:.*/)?\\.env(?:\\.(?!example$|sample$|template$).*)?") || lower.matches(".*(?:id_rsa|id_ed25519|credentials|secret-key|private-key|service-account).*")) throw unsafe("Remove credentials, dependency folders and repository internals.");
  }
  private static void inspectFile(String name,byte[] file) throws CharacterCodingException {
    if(name.matches(".*\\.(?:exe|dll|so|dylib|class|jar|war|pyc|pyo|bin|com|msi|apk|ipa|zip|gz|bz2|xz|7z|rar|tar|pem|p12|pfx|key)$")) throw unsafe("Executable binaries, nested archives and private keys are not accepted.");
    if((file.length>64 && file[0]=='M' && file[1]=='Z') || (file.length>=4 && file[0]==127 && file[1]=='E' && file[2]=='L' && file[3]=='F') || SECRET.matcher(new String(file,StandardCharsets.ISO_8859_1)).find()) throw unsafe("Executable binaries or credentials are not accepted.");
    if(name.endsWith(".png") && file.length>=8 && file[0]==(byte)137 && file[1]==80 && file[2]==78 && file[3]==71) return;
    if(name.matches(".*\\.jpe?g") && file.length>=3 && file[0]==(byte)255 && file[1]==(byte)216 && file[2]==(byte)255) return;
    if(name.endsWith(".webp") && file.length>=12 && new String(file,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(file,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return;
    String text=decode(file);
    if(text.indexOf('\0')>=0 || SECRET.matcher(text).find()) throw unsafe("Remove binary executables and live credentials from the source package.");
  }
}
