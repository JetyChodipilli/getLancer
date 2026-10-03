package com.getlancer.hosting;

import com.getlancer.shared.ApiError;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fixed operator configuration. No request can supply a publisher, gateway, or public origin. */
@Component
public final class HostingConfiguration {
  final boolean configured;final String reason,publisherSecret,gatewaySecret,publicTemplate;final URI publisher;final int expiryDays,timeoutMs;
  public HostingConfiguration(@Value("${app.hosting.enabled:false}") boolean enabled,@Value("${app.hosting.publisher-url:}") String publisherUrl,
      @Value("${app.hosting.publisher-secret:}") String publisherSecret,@Value("${app.hosting.public-url-template:}") String template,
      @Value("${app.hosting.gateway-secret:}") String gatewaySecret,@Value("${app.hosting.expiry-days:7}") int expiryDays,
      @Value("${app.hosting.timeout-ms:5000}") int timeoutMs,@Value("${app.origin}") String appOrigin) {
    this.publisherSecret=publisherSecret;this.gatewaySecret=gatewaySecret;publicTemplate=template;this.expiryDays=expiryDays;this.timeoutMs=timeoutMs;
    URI parsed=null;boolean valid=false;
    try {
      parsed=URI.create(publisherUrl);URI publicUrl=URI.create(template.replace("{id}","00000000-0000-0000-0000-000000000000"));URI app=URI.create(appOrigin);
      String host=publicUrl.getHost(),appHost=app.getHost();
      valid=origin(parsed)&&origin(publicUrl)&&template.indexOf("{id}")>=0&&template.indexOf("{id}")==template.lastIndexOf("{id}")
          &&host.startsWith("00000000-0000-0000-0000-000000000000.")&&!Objects.equals(host,parsed.getHost())
          &&isolated(publicUrl,appHost)
          &&publisherSecret.matches("[!-~]{32,256}")&&gatewaySecret.matches("[!-~]{32,256}")&&!publisherSecret.equals(gatewaySecret)
          &&expiryDays>=1&&expiryDays<=30&&timeoutMs>=500&&timeoutMs<=10000;
    }catch(RuntimeException e){valid=false;}
    publisher=parsed;configured=enabled&&valid;reason=enabled?(valid?"Ready for reviewed static frontend packages.":"Hosted demos need complete valid operator configuration."):"Hosted demos are disabled.";
  }
  private static boolean origin(URI uri) {
    String host=uri.getHost(),path=uri.getRawPath();if(host==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||path!=null&&!path.isEmpty()&&!path.equals("/"))return false;
    return uri.getScheme().equals("https")||uri.getScheme().equals("http")&&(host.equals("localhost")||host.endsWith(".localhost")||host.matches("[A-Za-z0-9-]+"));
  }
  private static boolean isolated(URI publicUrl,String appHost){
    String host=publicUrl.getHost();if(appHost==null||host.equals(appHost))return false;
    boolean development=host.equals("localhost")||host.endsWith(".localhost");
    if(development)return appHost.equals("localhost");
    if(!publicUrl.getScheme().equals("https"))return false;
    if(appHost.equals("localhost"))return true;
    // Conservative suffix check avoids shared parent cookies. Ambiguous multi-part suffixes fail closed.
    String[] app=appHost.toLowerCase(Locale.ROOT).split("\\."),demo=host.toLowerCase(Locale.ROOT).split("\\.");
    if(app.length<2||demo.length<2)return false;
    return !(app[app.length-2]+"."+app[app.length-1]).equals(demo[demo.length-2]+"."+demo[demo.length-1]);
  }
  public Map<String,Object> projection(){return Map.of("enabled",configured,"reason",reason,"maxCompressedBytes",StaticArchive.MAX_COMPRESSED,"maxExpandedBytes",StaticArchive.MAX_EXPANDED,"maxFiles",StaticArchive.MAX_FILES,"maxActive",3,"maxRecords",10,"expiryDays",expiryDays>=1&&expiryDays<=30?expiryDays:7);}
  public void ready(){if(!configured)throw new ApiError(503,"HOSTING_DISABLED",reason);}
  public String publicUrl(UUID id){ready();return publicTemplate.replace("{id}",id.toString()).replaceAll("/$","");}
  public boolean gateway(String supplied){return configured&&supplied!=null&&supplied.length()<=512&&MessageDigest.isEqual(gatewaySecret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));}
}
