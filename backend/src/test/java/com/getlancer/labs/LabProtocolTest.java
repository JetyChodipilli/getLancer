package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import com.getlancer.shared.ApiError;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabProtocolTest {
  @Test void publicRuntimeDefaultsDisabledAndEnabledFlagAloneCannotCertify(){var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    assertFalse(new LabConfiguration(false,"","","","","",3000,json).projection().enabled());
    assertFalse(new LabConfiguration(true,"https://operator.example","s".repeat(40),"","","",3000,json).projection().enabled());}
  @Test void fixedOriginsDenyExternalHttpCredentialsPathsFragmentsAndRedirectDestinations(){
    for(String url:java.util.List.of("http://evil.example","https://user:pass@operator.example","https://operator.example/path","https://operator.example?url=secret","https://operator.example#secret","file:///tmp/script"))assertFalse(LabConfiguration.validOrigin(URI.create(url)),url);
    assertTrue(LabConfiguration.validOrigin(URI.create("https://operator.example")));assertTrue(LabConfiguration.validOrigin(URI.create("http://127.0.0.1:8000")));}
  @Test void signedAdmissionAndManifestRequireExactEpochHashSignatureCurrentEvidence(){try(var fixture=new LabProtocolFixture()){
    var config=fixture.config();assertTrue(config.projection().enabled());var manifest=fixture.manifest(UUID.randomUUID());String payload;
    try{payload=fixture.json.writeValueAsString(manifest);}catch(Exception impossible){throw new IllegalStateException(impossible);}
    assertEquals(manifest,config.manifest(payload,LabConfiguration.hex(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),fixture.sign(payload),fixture.json));
    assertThrows(ApiError.class,()->config.manifest(payload,"0".repeat(64),fixture.sign(payload),fixture.json));
    assertThrows(ApiError.class,()->config.manifest(payload,LabConfiguration.hex(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),fixture.sign(payload+" "),fixture.json));
    assertFalse(new LabConfiguration(true,fixture.origin(),LabProtocolFixture.SECRET,fixture.publicKey(),fixture.evidence.toString(),UUID.randomUUID().toString(),1000,fixture.json).projection().enabled());
    fixture.writeEvidence(new LabConfiguration.Evidence("fixture","KVM",true,true,true,true,true,Instant.now().plusSeconds(32L*86400),fixture.evidenceHash,fixture.epoch));assertFalse(fixture.config().projection().enabled());
  }}
  @Test void admissionRejectsEnvelopeExtrasSymlinkAndMissingIndependentFacts()throws Exception{try(var fixture=new LabProtocolFixture()){
    var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)fixture.json.readTree(java.nio.file.Files.readString(fixture.evidence));envelope.put("approved",true);java.nio.file.Files.writeString(fixture.evidence,envelope.toString());assertFalse(fixture.config().projection().enabled());
    fixture.writeEvidence(new LabConfiguration.Evidence("fixture","KVM",false,true,true,true,true,Instant.now().plusSeconds(3600),fixture.evidenceHash,fixture.epoch));assertFalse(fixture.config().projection().enabled());
    fixture.writeEvidence(new LabConfiguration.Evidence("fixture","KVM",true,true,true,true,true,Instant.now().plusSeconds(3600),fixture.evidenceHash,fixture.epoch));
    var link=fixture.evidence.resolveSibling(fixture.evidence.getFileName()+"-link");java.nio.file.Files.createSymbolicLink(link,fixture.evidence);try{assertFalse(new LabConfiguration(true,fixture.origin(),LabProtocolFixture.SECRET,fixture.publicKey(),link.toString(),fixture.epoch.toString(),1000,fixture.json).projection().enabled());}finally{java.nio.file.Files.delete(link);}
  }}
  @Test void typedInputsPermitPositiveSchemaAndRejectUnknownOversizedOrOutOfRangeData(){try(var fixture=new LabProtocolFixture()){
    var manifest=fixture.manifest(UUID.randomUUID());var scenario=manifest.scenario("request");assertEquals(Map.of("message","Hello","count","2"),manifest.inputs(scenario,Map.of("message","Hello","count","2")));
    for(Map<String,String> values:java.util.List.of(Map.of("message","hello","url","https://evil.test"),Map.of("message","x".repeat(121)),Map.of("message","hello","count","99"),Map.of("message","hello","count","$(ls)"),Map.<String,String>of()))assertThrows(ApiError.class,()->manifest.inputs(scenario,values));
    assertThrows(ApiError.class,()->manifest.scenario("shell"));
  }}
  private LabProviderClient.Command command(LabProtocolFixture fixture){return new LabProviderClient.Command(UUID.randomUUID(),1,fixture.epoch,UUID.randomUUID(),"a".repeat(64),"sha256:"+"b".repeat(64),Instant.now().plusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MILLIS),"START","request",null,Map.of("message","hello"),64);}
  @Test void authenticatedBoundedHttpProtocolHasRealPositiveResponseAndExactBindingNegatives(){try(var fixture=new LabProtocolFixture()){
    var client=new LabProviderClient(fixture.config(),fixture.json);var command=command(fixture);var result=client.send(command);assertEquals("RUNNING",result.state());assertTrue(result.healthy());assertTrue(result.isolated());assertEquals(command,fixture.commands.get(command.commandId()));
    fixture.wrongBinding=true;assertThrows(ApiError.class,()->client.send(command));fixture.reset();fixture.redirect=true;assertThrows(ApiError.class,()->client.send(command));fixture.reset();fixture.oversized=true;assertThrows(ApiError.class,()->client.send(command));fixture.reset();fixture.notIsolated=true;assertThrows(ApiError.class,()->client.send(command));
  }}
  @Test void operationResultRequiresHealthyIsolatedExecution(){try(var fixture=new LabProtocolFixture()){
    var client=new LabProviderClient(fixture.config(),fixture.json);var start=command(fixture);client.send(start);var operation=new LabProviderClient.Command(start.runId(),1,fixture.epoch,UUID.randomUUID(),start.manifestSha256(),start.imageDigest(),start.expiresAt(),"REQUEST",start.scenarioId(),"echo",Map.of("message","Hello"),64);assertEquals("RESULT",client.send(operation).state());fixture.notIsolated=true;assertEquals("LAB_ISOLATION_LOST",assertThrows(ApiError.class,()->client.send(operation)).code);
  }}
  @Test void providerDeadlineIncludesBodyAfterImmediateSuccessfulHeaders(){try(var fixture=new LabProtocolFixture()){
    var client=new LabProviderClient(fixture.config(),fixture.json);fixture.slowBody=true;long started=System.nanoTime();assertThrows(ApiError.class,()->client.send(command(fixture)));long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);assertTrue(elapsed<2200,"The body deadline must close a stalled provider body, measured "+elapsed+" ms");
  }}
  @Test void staleProviderLeaseCannotStartAfterCancellationFence(){try(var fixture=new LabProtocolFixture()){
    var client=new LabProviderClient(fixture.config(),fixture.json);var command=command(fixture);var stop=new LabProviderClient.Command(command.runId(),2,fixture.epoch,UUID.randomUUID(),command.manifestSha256(),command.imageDigest(),command.expiresAt(),"STOP",command.scenarioId(),null,Map.of(),64);assertEquals("CLEANED",client.send(stop).state());assertThrows(ApiError.class,()->client.send(command));
  }}
}
