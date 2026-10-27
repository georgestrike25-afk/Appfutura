package com.cumbre.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Crea (o recupera) un ViewModel con dependencias explícitas. */
@Composable
inline fun <reified VM : ViewModel> cumbreViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })
