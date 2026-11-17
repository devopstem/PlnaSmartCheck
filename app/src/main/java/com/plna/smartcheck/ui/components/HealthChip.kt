package com.plna.smartcheck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plna.smartcheck.R
import com.plna.smartcheck.data.health.ProductHealth
import com.plna.smartcheck.data.health.ProductHealthColors

@Composable
fun HealthChip(
    health: ProductHealth,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val (fg, bg, labelRes) = when (health) {
        ProductHealth.HEALTHY -> Triple(
            ProductHealthColors.Healthy,
            ProductHealthColors.HealthyBg,
            R.string.health_healthy,
        )
        ProductHealth.HARMFUL -> Triple(
            ProductHealthColors.Harmful,
            ProductHealthColors.HarmfulBg,
            R.string.health_harmful,
        )
        ProductHealth.NEUTRAL -> Triple(
            ProductHealthColors.Neutral,
            ProductHealthColors.NeutralBg,
            R.string.health_neutral,
        )
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(labelRes),
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
