package io.branch.delayedinittest

import android.app.Application
import io.branch.referral.Branch
import io.branch.referral.BranchLogger.BranchLogLevel

class DelayedInitTestApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        Branch.expectDelayedSessionInitialization(true)

        Branch.enableLogging(BranchLogLevel.VERBOSE)

        Branch.getAutoInstance(this)
    }
}