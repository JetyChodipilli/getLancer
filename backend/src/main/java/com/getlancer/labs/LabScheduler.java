package com.getlancer.labs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class LabScheduler {
  private final LabService service;private final boolean enabled;
  public LabScheduler(LabService service,@Value("${app.jobs-enabled:true}") boolean enabled){this.service=service;this.enabled=enabled;}
  @Scheduled(fixedDelayString="${app.labs.watchdog-ms:5000}") public void watchdog(){if(enabled)try{service.tick();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(LabScheduler.class).warn("Lab watchdog needs operator attention: {}",failure.getClass().getSimpleName());}}
}
