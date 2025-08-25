package com.example.mine.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mine.network.CommunicationProof
import com.example.mine.network.ProofType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationProofScreen(
    proofs: List<CommunicationProof>,
    isVerifying: Boolean,
    verificationProgress: Float,
    onBack: () -> Unit,
    onVerifyCommunication: () -> Unit,
    onClearProofs: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A), // blue-900
                        Color(0xFF7C3AED), // purple-600
                        Color(0xFF000000)  // black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            CommunicationProofHeader(
                onBack = onBack,
                onVerify = onVerifyCommunication,
                onClear = onClearProofs,
                isVerifying = isVerifying
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress indicator
            if (isVerifying) {
                VerificationProgressCard(verificationProgress)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Device Information Card
            DeviceInformationCard(proofs)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary card
            if (proofs.isNotEmpty()) {
                CommunicationSummaryCard(proofs)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Proofs list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(proofs) { proof ->
                    ProofCard(proof = proof, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
fun CommunicationProofHeader(
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onClear: () -> Unit,
    isVerifying: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Communication Proof",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Verification Results",
                    fontSize = 14.sp,
                    color = Color(0xFF93C5FD) // blue-200
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onVerify,
                    enabled = !isVerifying
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Verify",
                        tint = if (isVerifying) Color.Gray else Color.White
                    )
                }
                
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun VerificationProgressCard(progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing)
                    ),
                    label = "rotation"
                )
                
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Verifying",
                    tint = Color(0xFFF59E0B), // amber-500
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
                
                Text(
                    text = "Verifying Communication...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF10B981), // emerald-500
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 14.sp,
                color = Color(0xFF10B981), // emerald-500
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CommunicationSummaryCard(proofs: List<CommunicationProof>) {
    val successfulTests = proofs.count { it.success }
    val totalTests = proofs.size
    val successRate = if (totalTests > 0) (successfulTests * 100.0 / totalTests).toInt() else 0
    
    val avgLatency = proofs.mapNotNull { it.latency }.average()
    val latencyStr = if (avgLatency.isFinite()) "${avgLatency.toInt()}ms" else "N/A"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Communication Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    icon = Icons.Default.CheckCircle,
                    label = "Success Rate",
                    value = "$successRate%",
                    color = if (successRate >= 80) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                
                SummaryItem(
                    icon = Icons.Default.Speed,
                    label = "Avg Latency",
                    value = latencyStr,
                    color = Color(0xFF3B82F6)
                )
                
                SummaryItem(
                    icon = Icons.Default.Wifi,
                    label = "Connection",
                    value = proofs.firstOrNull()?.connectionType?.uppercase() ?: "Unknown",
                    color = Color(0xFF8B5CF6)
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun ProofCard(
    proof: CommunicationProof,
    dateFormat: SimpleDateFormat
) {
    val backgroundColor = if (proof.success) {
        Color(0xFF10B981).copy(alpha = 0.2f) // emerald with transparency
    } else {
        Color(0xFFEF4444).copy(alpha = 0.2f) // red with transparency
    }
    
    val borderColor = if (proof.success) {
        Color(0xFF10B981) // emerald
    } else {
        Color(0xFFEF4444) // red
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = getProofTypeIcon(proof.proofType),
                        contentDescription = proof.proofType.name,
                        tint = if (proof.success) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = getProofTypeDisplayName(proof.proofType),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (proof.latency != null) {
                        Text(
                            text = "${proof.latency}ms",
                            fontSize = 12.sp,
                            color = Color(0xFF3B82F6),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Icon(
                        imageVector = if (proof.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = if (proof.success) "Success" else "Failed",
                        tint = if (proof.success) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Proof data
            Text(
                text = proof.proofData,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
            
            // Error message if any
            proof.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: $error",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(proof.timestamp)),
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
                
                Text(
                    text = "Device: ${proof.deviceId}",
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun getProofTypeIcon(proofType: ProofType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (proofType) {
        ProofType.PING_PONG -> Icons.Default.RadioButtonChecked
        ProofType.ECHO_TEST -> Icons.Default.Refresh
        ProofType.KEY_EXCHANGE -> Icons.Default.VpnKey
        ProofType.MESSAGE_DELIVERY -> Icons.Default.Message
        ProofType.SIGNAL_STRENGTH -> Icons.Default.SignalCellular4Bar
        ProofType.BANDWIDTH_TEST -> Icons.Default.Speed
        ProofType.ENCRYPTION_VERIFICATION -> Icons.Default.Lock
    }
}

fun getProofTypeDisplayName(proofType: ProofType): String {
    return when (proofType) {
        ProofType.PING_PONG -> "Ping-Pong Test"
        ProofType.ECHO_TEST -> "Echo Test"
        ProofType.KEY_EXCHANGE -> "Key Exchange"
        ProofType.MESSAGE_DELIVERY -> "Message Delivery"
        ProofType.SIGNAL_STRENGTH -> "Signal Strength"
        ProofType.BANDWIDTH_TEST -> "Bandwidth Test"
        ProofType.ENCRYPTION_VERIFICATION -> "Encryption"
    }
}

@Composable
fun DeviceInformationCard(proofs: List<CommunicationProof>) {
    val firstProof = proofs.firstOrNull()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Device Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (firstProof != null) {
                // Device Name
                firstProof.deviceName?.let { deviceName ->
                    InfoRow(
                        icon = Icons.Default.DeviceHub,
                        label = "Device Name",
                        value = deviceName,
                        color = Color(0xFF3B82F6)
                    )
                }
                
                // Device ID
                InfoRow(
                    icon = Icons.Default.Fingerprint,
                    label = "Device ID",
                    value = firstProof.deviceId,
                    color = Color(0xFF8B5CF6)
                )
                
                // Connection Type
                InfoRow(
                    icon = if (firstProof.connectionType.lowercase() == "wifi") Icons.Default.Wifi else Icons.Default.Bluetooth,
                    label = "Connection Type",
                    value = firstProof.connectionType.uppercase(),
                    color = if (firstProof.connectionType.lowercase() == "wifi") Color(0xFF8B5CF6) else Color(0xFF06B6D4)
                )
                
                // Connection Status
                InfoRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Connection Status",
                    value = "Connected",
                    color = Color(0xFF10B981)
                )
                
                // Last Verified
                InfoRow(
                    icon = Icons.Default.Schedule,
                    label = "Last Verified",
                    value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(firstProof.timestamp)),
                    color = Color(0xFFF59E0B)
                )
                
                // Device Type (inferred from connection type)
                val deviceType = when (firstProof.connectionType.lowercase()) {
                    "wifi" -> "Wi-Fi Network"
                    "bluetooth" -> "Bluetooth Device"
                    else -> "Unknown Device"
                }
                
                InfoRow(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Device Type",
                    value = deviceType,
                    color = Color(0xFFEC4899)
                )
                
                // Security Level
                InfoRow(
                    icon = Icons.Default.Security,
                    label = "Security Level",
                    value = "High (AES-256)",
                    color = Color(0xFF10B981)
                )
                
                // Protocol Version
                InfoRow(
                    icon = Icons.Default.Code,
                    label = "Protocol Version",
                    value = "Fusion v1.0",
                    color = Color(0xFF6B7280)
                )
                
            } else {
                // No proofs available yet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Click 'Verify Communication' to see device details",
                        fontSize = 14.sp,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.8f)
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
