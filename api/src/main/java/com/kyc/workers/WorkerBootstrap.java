package com.kyc.workers;

import com.kyc.RecognizMeApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WorkerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerBootstrap.class);

    @Override
    public void run(ApplicationArguments args) {
        log.info("Recogniz-Me workers ready (SQS consumers will be wired in sprint 2+)");
    }
}
