package io.branch.branchandroidtestbed;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import io.branch.interfaces.IBranchLoggingCallbacks;
import io.branch.referral.Branch;
import io.branch.referral.BranchLogger;
import io.branch.referral.Defines;

public final class CustomBranchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        IBranchLoggingCallbacks loggingCallbacks = (message, tag) -> {
            Log.d("BranchSDK", message);
            saveLogToFile(message);
        };
        Branch.enableLogging(loggingCallbacks);

        //Branch.enableLogging(BranchLogger.BranchLogLevel.VERBOSE);
        Branch.getAutoInstance(this);
    }

    private void saveLogToFile(String logMessage) {
        File logFile = new File(getFilesDir(), "branchlogs.txt");

        try {
            if (!logFile.exists()) {
                boolean fileCreated = logFile.createNewFile();
                Log.d("BranchTestbed", "Log file created: " + fileCreated);
            }

            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(logMessage + "\n\n");
            }

        } catch (Exception e) {
            Log.e("BranchTestbed", "Error writing to log file", e);
        }
    }


}
