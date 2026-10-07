package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.EBomaDatabase
import com.example.data.repository.EBomaRepository
import com.example.ui.theme.AlertSosRed
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardBackgroundWhite
import com.example.ui.theme.DeepTealNavy
import com.example.ui.theme.DeepTealNavyDark
import com.example.ui.theme.KenyanGreen
import com.example.ui.theme.KenyanRed
import com.example.ui.theme.KenyanWhite
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: EBomaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DeepTealNavyDark
                )
                { innerPadding ->
                    EBomaSetupConfirmationScreen(
                        modifier = Modifier.padding(innerPadding),
                        repository = repository
                    )
                }
            }
        }
    }
}

@Composable
fun EBomaSetupConfirmationScreen(
    modifier: Modifier = Modifier,
    repository: EBomaRepository
) {
    var isInitialized by remember { mutableStateOf(false) }
    var seedCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            repository.seedKisumuStaycationDataIfEmpty()
            val listings = repository.getAllListingsForAdminAudit()
            seedCount = listings.size
            isInitialized = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Shield Crest Brand Mark with Kenyan flag colors + eBoma wordmark
        BrandCrestHeader()

        Spacer(modifier = Modifier.height(24.dp))

        if (!isInitialized) {
            CircularProgressIndicator(
                color = SuccessPaidGreen,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Initializing e-Boma Core Data Models & Rule Engine...",
                color = KenyanWhite,
                fontSize = 14.sp
            )
        } else {
            // Confirmation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SuccessPaidGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Confirmed",
                                tint = SuccessPaidGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Data Model & Rules Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Kisumu Staycation Escrow Architecture",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "9 Data Entities Ready in Room DB:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = DeepTealNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val entities = listOf(
                        "Users (Role: Guest/Host, KYC Status, Payout Phone)",
                        "Listings (Kisumu stays, KYC & Physical Inspection flags, KSh)",
                        "Bookings (Group stay, Dates, QR check-in timestamp)",
                        "GroupMembers (M-Pesa split contributions, Payment status)",
                        "Payments (M-Pesa STK push reference, Timestamps)",
                        "EscrowTransactions (Locked funds, 24h auto-release, Freeze)",
                        "HostVerification (ID doc, Liveness selfie, KRA PIN, KPLC meter)",
                        "Reviews (Tags, Ratings, Verified stays)",
                        "SOSAlerts (Kisumu GPS coordinates, Security dispatch)"
                    )
                    entities.forEach { entity ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(KenyanGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entity,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "5 Core Rules Enforced in Logic:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = DeepTealNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    RuleStatusItem(
                        number = 1,
                        title = "Listing Visibility Gate",
                        description = "Requires BOTH is_kyc_verified AND is_physical_inspected = true. Unverified stays are fully hidden from guests."
                    )
                    RuleStatusItem(
                        number = 2,
                        title = "Host KYC 3-Check Lock",
                        description = "Next button disabled until ID scan, liveness selfie, and KRA PIN entry are all complete."
                    )
                    RuleStatusItem(
                        number = 3,
                        title = "Payout Phone Fraud Freeze",
                        description = "Changing M-Pesa payout number within 48h of payout automatically flags account & freezes payout."
                    )
                    RuleStatusItem(
                        number = 4,
                        title = "24h Escrow Release & Guest Freeze",
                        description = "Escrow releases 24h after QR scan unless guest taps 'Freeze Escrow' during the 24h window."
                    )
                    RuleStatusItem(
                        number = 5,
                        title = "Separate Damage Deposit Release",
                        description = "Deposit tracked separately in KSh, releases only after both guest & host confirm clean checkout."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DeepTealNavy.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "Awaiting prompt to generate screens.",
                    color = KenyanWhite.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun BrandCrestHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DeepTealNavy)
                .border(2.dp, KenyanGreen, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_eboma_crest),
                contentDescription = "e-Boma Kenyan Shield Crest",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "eBoma",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = KenyanWhite,
            letterSpacing = 1.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(KenyanRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Kisumu Staycations • M-Pesa Escrow",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = KenyanWhite.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(KenyanGreen)
            )
        }
    }
}

@Composable
fun RuleStatusItem(
    number: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(SuccessPaidGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = SuccessPaidGreen
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
