package com.plna.smartcheck.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.plna.smartcheck.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlnaHomeTopBar(
    onOpenProfile: () -> Unit,
) {
    TopAppBar(
        title = {
            Image(
                painter = painterResource(R.drawable.plna_brand_logo),
                contentDescription = "PLNA",
                modifier = Modifier
                    .height(32.dp)
                    .padding(end = 4.dp),
                contentScale = ContentScale.Fit,
            )
        },
        actions = {
            IconButton(onClick = onOpenProfile) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Личный кабинет",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlnaScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}
