package app.noraproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP connect time only. NOT an Xray handshake, proxy RTT, tunnel success, or throughput test.
 * Each test occurs on the customer's own physical network.
 */
object EndpointProbe {
    private const val TIMEOUT_MS = 1_800
    private const val PARALLEL = 12
    private const val MAX_NODES = 250

    private suspend fun connect(node: ProxyNode): Long? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(node.host, node.port), TIMEOUT_MS)
                ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
            }
        }.getOrNull()
    }

    suspend fun test(
        nodes: List<ProxyNode>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, ProbeStats> = coroutineScope {
        val bounded = nodes.take(MAX_NODES)
        val semaphore = Semaphore(PARALLEL)
        val count = AtomicInteger(0)
        val first = bounded.map { node ->
            async {
                val sample = semaphore.withPermit { connect(node) }
                onProgress(count.incrementAndGet(), bounded.size)
                node.id to ProbeStats(listOfNotNull(sample), 1)
            }
        }.awaitAll().toMap().toMutableMap()

        // Confirm leading routes rather than trusting a single noisy TCP sample.
        val shortlist = RouteSelector.rank(bounded, first).filter { it.stats.reachable }.take(8)
        shortlist.map { candidate ->
            async {
                val samples = mutableListOf<Long>()
                repeat(2) { semaphore.withPermit { connect(candidate.node) }?.let(samples::add) }
                candidate.node.id to samples
            }
        }.awaitAll().forEach { (id, samples) ->
            val old = first.getValue(id)
            first[id] = ProbeStats(old.samples + samples, old.attempted + 2)
        }
        first.toMap()
    }
}
