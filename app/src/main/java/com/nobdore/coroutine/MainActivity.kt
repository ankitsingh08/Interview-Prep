package com.nobdore.coroutine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobdore.coroutine.ui.theme.CoroutineTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoroutineTheme {
                CoroutineSandbox()
            }
        }
    }
}

@Composable
fun CoroutineSandbox() {
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    
    // Lab 5 & 6 State
    val timerStateFlow = remember { MutableStateFlow(0) }
    val timerValue by timerStateFlow.collectAsStateWithLifecycle()
    
    val eventSharedFlow = remember { MutableSharedFlow<String>() }
    val eventChannel = remember { Channel<String>() }

    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logs.add(0, "[$time] $message")
    }

    // THE FIX: Collect only once for the lifetime of this Composable
    LaunchedEffect(Unit) {
        eventSharedFlow.collect { event ->
            log("Lab 5: SharedFlow Event -> $event")
        }
    }
    
    LaunchedEffect(Unit) {
        // Channels are collected as flows
        for (event in eventChannel) {
            log("Lab 6: Channel Event -> $event")
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Coroutine Mastery Sandbox",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = "Lab 5 Timer: $timerValue",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Blue,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lab Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 1: Starting Task on Main Thread")
                                val result = withContext(Dispatchers.IO) {
                                    log("Lab 1: Working on IO Thread...")
                                    delay(2000)
                                    "Success from IO"
                                }
                                log("Lab 1: Back on Main Thread with: $result")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 1: Launch", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 2: Fetching data in parallel...")
                                val startTime = System.currentTimeMillis()
                                
                                val profileDeferred = async(Dispatchers.IO) {
                                    delay(2000)
                                    "Profile Data"
                                }
                                
                                val postsDeferred = async(Dispatchers.IO) {
                                    delay(2000)
                                    "Recent Posts"
                                }
                                
                                val profile = profileDeferred.await()
                                val posts = postsDeferred.await()
                                
                                val duration = System.currentTimeMillis() - startTime
                                log("Lab 2: Done in ${duration}ms (Expected ~2000ms)")
                                log("Lab 2: $profile & $posts")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 2: Async", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val handler = CoroutineExceptionHandler { _, exception ->
                                log("Lab 3: Caught by ExceptionHandler: ${exception.message}")
                            }

                            scope.launch(handler) {
                                log("Lab 3: Starting with try-catch...")
                                try {
                                    log("Lab 3: Attempting risky task...")
                                    delay(1000)
                                    throw Exception("Database Error!")
                                } catch (e: Exception) {
                                    log("Lab 3: Caught in try-catch: ${e.message}")
                                }

                                log("Lab 3: Attempting task that will fail Handler...")
                                delay(1000)
                                throw Exception("Uncaught Network Error!")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 3: Errors", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val handler = CoroutineExceptionHandler { _, exception ->
                                log("Lab 4: Caught by ExceptionHandler: ${exception.message}")
                            }

                            scope.launch(handler) {
                                log("Lab 4: Starting Supervisor Lab...")
                                supervisorScope {
                                    launch {
                                        log("Lab 4: Child 1 failing...")
                                        delay(500)
                                        throw Exception("Child 1 Crashed!")
                                    }
                                    launch {
                                        log("Lab 4: Child 2 started...")
                                        delay(1000)
                                        log("Lab 4: Child 2 (I survived because of supervisorScope!)")
                                    }
                                }
                                log("Lab 4: Parent coroutine is still alive!")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 4: Supervisor", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 5: Emitting to SharedFlow...")
                                flow {
                                    emit(1)
                                    emit(2)
                                    emit(3)
                                }
                                .filter { it > 1 }
                                .collect { log("Lab 5: Flow emission -> $it") }

                                eventSharedFlow.emit("Shared Event @ ${System.currentTimeMillis()}")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 5: Flows", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 6: Sending to Channel...")
                                eventChannel.send("Channel Event @ ${System.currentTimeMillis()}")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 6: Channel", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val job = scope.launch(Dispatchers.Default) {
                                log("Lab 7: Heavy task started...")
                                var count = 0
                                val startTime = System.currentTimeMillis()
                                while (count < 1000000) {
                                    // ensureActive() // Try uncommenting this!
                                    // yield()        // Or try this!
                                    count++
                                    if (count % 100000 == 0) {
                                        log("Lab 7: Progress $count")
                                    }
                                }
                                log("Lab 7: Task finished in ${System.currentTimeMillis() - startTime}ms")
                            }
                            
                            scope.launch {
                                delay(100)
                                log("Lab 7: Cancelling task...")
                                job.cancel()
                                log("Lab 7: Job cancelled, but did it stop? (Check logs)")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 7: Cancellation", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 8: Starting flowOn Demo...")
                                flow {
                                    log("Lab 8: Emitting on ${Thread.currentThread().name}")
                                    emit("Data")
                                }
                                .flowOn(Dispatchers.IO) // Upstream runs on IO
                                .collect {
                                    log("Lab 8: Collected on ${Thread.currentThread().name}")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 8: flowOn", fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 9: callbackFlow started...")
                                
                                // Simulating a legacy listener API
                                val legacyFlow = callbackFlow {
                                    val timer = Timer()
                                    val listener = object : TimerTask() {
                                        var count = 0
                                        override fun run() {
                                            count++
                                            trySend(count)
                                            if (count >= 3) {
                                                log("Lab 9: Reached limit, closing flow...")
                                                close() // Signals that the flow is finished
                                            }
                                        }
                                    }
                                    timer.schedule(listener, 0, 1000)
                                    
                                    awaitClose {
                                        log("Lab 9: awaitClose executed - Cleaning up timer!")
                                        timer.cancel()
                                    }
                                }

                                legacyFlow.collect { log("Lab 9: Received from legacy: $it") }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 9: Bridge", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            log("Lab 10: runBlocking started (UI will freeze!)")
                            runBlocking {
                                // DON'T DO THIS IN PROD
                                delay(3000) 
                                log("Lab 10: runBlocking finished")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 10: Block", fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 11: Starting Mutex Race...")
                                var counter = 0
                                val mutex = Mutex()
                                
                                // Launch 100 coroutines each incrementing 100 times
                                val jobs = List(100) {
                                    launch(Dispatchers.Default) {
                                        repeat(100) {
                                            mutex.withLock { // Try commenting this to see it break!
                                                counter++
                                            }
                                        }
                                    }
                                }
                                jobs.forEach { it.join() }
                                log("Lab 11: Final Count = $counter (Expected 10000)")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 11: Mutex", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                log("Lab 12: Flow Error handling...")
                                flow {
                                    emit("Step 1")
                                    throw RuntimeException("Flow Crash!")
                                }
                                .catch { e -> 
                                    log("Lab 12: Caught in .catch operator: ${e.message}")
                                    emit("Recovered State")
                                }
                                .onCompletion { log("Lab 12: Flow Completed") }
                                .collect { log("Lab 12: Received: $it") }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lab 12: .catch", fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { logs.clear() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Logs")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text("Console Logs:", style = MaterialTheme.typography.labelLarge)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                items(logs) { logEntry ->
                    Text(
                        text = logEntry,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (logEntry.contains("Error", true)) Color.Red else Color.Unspecified,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
