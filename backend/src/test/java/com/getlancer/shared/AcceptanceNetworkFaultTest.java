package com.getlancer.shared;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Deterministic address-boundary faults without live DNS or metadata access. */
class AcceptanceNetworkFaultTest {
  @Test void metadataPrivateIpv6AndEveryMixedAnswerAreDeniedAfterAPublicPositiveControl()throws Exception{
    var publicAddress=InetAddress.getByAddress(new byte[]{8,8,8,8});
    Rules.safeUrl("https://example.test/source",host->new InetAddress[]{publicAddress});
    for(String literal:new String[]{"169.254.169.254","10.0.0.1","127.0.0.1","100.64.0.1","192.168.0.1","::1","fe80::1","fc00::1","ff02::1","::ffff:127.0.0.1"}){
      var denied=InetAddress.getByName(literal);assertFalse(Rules.publicAddress(denied),literal);
      assertThrows(IllegalArgumentException.class,()->Rules.safeUrl("https://example.test/source",host->new InetAddress[]{publicAddress,denied}),literal);
    }
    for(String raw:new String[]{"https://169.254.169.254/latest/meta-data","https://[::1]/","https://internal.local/","https://a:b@example.test/","http://example.test/","https://example.test:6379/","https://example.test:5432/"})assertThrows(IllegalArgumentException.class,()->Rules.safeUrl(raw,host->new InetAddress[]{publicAddress}),raw);
  }
  @Test void repeatedValidationNeverReusesAPreviousPublicDnsAnswer()throws Exception{
    var publicAddress=InetAddress.getByAddress(new byte[]{8,8,8,8});var metadata=InetAddress.getByAddress(new byte[]{(byte)169,(byte)254,(byte)169,(byte)254});
    var lookups=new AtomicInteger();Rules.AddressLookup rebinding=host->lookups.getAndIncrement()==0?new InetAddress[]{publicAddress}:new InetAddress[]{metadata};
    Rules.safeUrl("https://example.test/package",rebinding);
    assertThrows(IllegalArgumentException.class,()->Rules.safeUrl("https://example.test/package",rebinding));assertEquals(2,lookups.get());
    assertThrows(IllegalArgumentException.class,()->Rules.safeUrl("https://example.test/package",host->{throw new IOException("Synthetic resolver timeout");}));
  }
}
