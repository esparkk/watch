/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.passivedata

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.passivedata.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Activity displaying the app UI. Notably, this binds data from [MainViewModel] to views on screen,
 * and performs the permission check when enabling passive data.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                when (result) {
                    true -> {
                        Log.i(TAG, "Body sensors permission granted")
                        viewModel.togglePassiveData(true)
                    }
                    false -> {
                        Log.i(TAG, "Body sensors permission not granted")
                        viewModel.togglePassiveData(false)
                    }
                }
            }

        binding.enablePassiveData.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Make sure we have the necessary permission first.
                permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
            } else {
                viewModel.togglePassiveData(false)
            }
        }

        // Bind viewmodel state to the UI.
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect {
                updateViewVisiblity(it)
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.latestHeartRate.collect {
                binding.lastMeasuredValue.text = it.toString()
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.passiveDataEnabled.collect {
                binding.enablePassiveData.isChecked = it
            }
        }
    }

    private fun updateViewVisiblity(uiState: UiState) {
        (uiState is UiState.Startup).let {
            binding.progress.isVisible = it
        }
        // These views are visible when heart rate capability is not available.
        (uiState is UiState.HeartRateNotAvailable).let {
            binding.brokenHeart.isVisible = it
            binding.notAvailable.isVisible = it
        }
        // These views are visible when the capability is available.
        (uiState is UiState.HeartRateAvailable).let {
            binding.enablePassiveData.isVisible = it
            binding.lastMeasuredLabel.isVisible = it
            binding.lastMeasuredValue.isVisible = it
            binding.heart.isVisible = it
        }
    }
}


class HttpGetRequest : AsyncTask<String?, Void?, String?>() {
    protected override fun doInBackground(vararg params: String): String? {
        val urlString = params[0] // URL to call
        var result = ""
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.setRequestMethod("GET")
            val responseCode = urlConnection!!.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val `in` = BufferedReader(
                    InputStreamReader(
                        urlConnection.inputStream
                    )
                )
                var inputLine: String?
                val response = StringBuilder()
                while (`in`.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                `in`.close()
                result = response.toString()
            } else {
                result = "GET request failed. Response Code: $responseCode"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result = e.message
        } finally {
            urlConnection?.disconnect()
        }
        return result
    }

    override fun onPostExecute(result: String?) {
        // This is where you can update the UI with the result
        super.onPostExecute(result)
        println("HTTP GET Response: $result")
    }
}



