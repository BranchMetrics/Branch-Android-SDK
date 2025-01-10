package io.branch.delayedinittest

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.branch.delayedinittest.ui.theme.DelayedInitTestTheme
import io.branch.referral.Branch
import android.content.Intent
import android.app.PendingIntent


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DelayedInitTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DelayedBranchInitUI { initializeBranch() }
                }
            }
        }
    }

    private fun initializeBranch() {
        Branch.sessionBuilder(this).withCallback { branchUniversalObject, linkProperties, error ->
            if (error != null) {
                Log.e("BranchSDK_Tester", "Branch init failed. Caused by - ${error.message}")
            } else {
                Log.i("BranchSDK_Tester", "Branch init complete!")

                val deepLinkDetails = StringBuilder()
                branchUniversalObject?.let {
                    deepLinkDetails.append("Title: ${it.title}\n")
                    deepLinkDetails.append("Canonical Identifier: ${it.canonicalIdentifier}\n")
                    deepLinkDetails.append("Metadata: ${it.contentMetadata.convertToJson()}\n")
                }
                linkProperties?.let {
                    deepLinkDetails.append("Channel: ${it.channel}\n")
                    deepLinkDetails.append("Control Params: ${it.controlParams}\n")
                }

                if (deepLinkDetails.isNotEmpty()) {
                    showDeepLinkAlert(deepLinkDetails.toString())
                }
            }
        }.withData(this.intent.data).init()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent

        if (intent.hasExtra("branch_force_new_session") && intent.getBooleanExtra("branch_force_new_session", false)) {
            Branch.sessionBuilder(this).withCallback { branchUniversalObject, linkProperties, error ->
                if (error != null) {
                    Log.e("BranchSDK_Tester", error.message ?: "Unknown error")
                } else {
                    val deepLinkDetails = StringBuilder()
                    branchUniversalObject?.let {
                        deepLinkDetails.append("Title: ${it.title}\n")
                        deepLinkDetails.append("Canonical Identifier: ${it.canonicalIdentifier}\n")
                        deepLinkDetails.append("Metadata: ${it.contentMetadata.convertToJson()}\n")
                    }
                    linkProperties?.let {
                        deepLinkDetails.append("Channel: ${it.channel}\n")
                        deepLinkDetails.append("Control Params: ${it.controlParams}\n")
                    }

                    if (deepLinkDetails.isNotEmpty()) {
                        showDeepLinkAlert(deepLinkDetails.toString())
                    }
                }
            }.reInit()
        }
    }

    // Function to display an alert dialog
    private fun showDeepLinkAlert(details: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Deep Link Details")
        builder.setMessage(details)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }
}

@Composable
fun DelayedBranchInitUI(onInitClick: () -> Unit) {
    var isInitialized by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        if (isInitialized) {
            Text("Branch SDK Initialized!")
        } else {
            Button(onClick = {
                onInitClick()
                isInitialized = true
            }) {
                Text("Initialize Branch SDK")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DelayedBranchInitPreview() {
    DelayedInitTestTheme {
        DelayedBranchInitUI {}
    }
}