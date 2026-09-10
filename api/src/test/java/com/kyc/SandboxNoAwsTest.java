package com.kyc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kyc.adapters.StubBiometricAi;
import com.kyc.adapters.StubDocumentAi;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SandboxNoAwsTest {

    @Autowired
    private DocumentAiPort documentAi;

    @Autowired
    private BiometricAiPort biometricAi;

    @Test
    void sandboxUsesStubAdaptersAndHasNoAwsClasses() {
        assertInstanceOf(StubDocumentAi.class, documentAi);
        assertInstanceOf(StubBiometricAi.class, biometricAi);
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.kyc.adapters.AwsDocumentAi"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.kyc.adapters.AwsBiometricAi"));
    }
}
