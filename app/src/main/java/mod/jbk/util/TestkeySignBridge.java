package mod.jbk.util;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import mod.alucard.tn.apksigner.ApkSigner;

public class TestkeySignBridge {
    private TestkeySignBridge() {
    }

    public static void signWithTestkey(String inputPath, String outputPath) throws GeneralSecurityException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        ApkSigner signer = new ApkSigner();
        signer.signWithTestKey(inputPath, outputPath, null);

        File outputFile = new File(outputPath);
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("APK signing failed: signed output not created at " + outputPath);
        }
    }
}
