package app.noraproxy.core

data class ProbeStats(
    val samples: List<Long>,
    val attempted: Int
) {
    val reachable: Boolean get() = samples.isNotEmpty()
    val latencyMs: Long? get() = if (!reachable) null else samples.sorted()[samples.size / 2]
    val jitterMs: Long get() =
        if (samples.size < 2) 0L else samples.maxOrNull()!! - samples.minOrNull()!!
    val successPercent: Int get() =
        if (attempted <= 0) 0 else samples.size * 100 / attempted
}

data class RankedNode(val node: ProxyNode, val stats: ProbeStats, val score: Double)

object RouteSelector {
    fun rank(nodes: List<ProxyNode>, measurements: Map<String, ProbeStats>): List<RankedNode> =
        nodes.map { node ->
            val stats = measurements[node.id] ?: ProbeStats(emptyList(), 0)
            val score = if (!stats.reachable) Double.NEGATIVE_INFINITY
            else stats.successPercent * 10.0 -
                stats.latencyMs!!.coerceAtMost(3000L) * 0.50 -
                stats.jitterMs.coerceAtMost(3000L) * 0.20
            RankedNode(node, stats, score)
        }.sortedWith(
            compareByDescending<RankedNode> { it.stats.reachable }
                .thenByDescending { it.score }
                .thenBy { it.stats.latencyMs ?: Long.MAX_VALUE }
        )

    fun bestPerRegion(ranked: List<RankedNode>): List<RankedNode> =
        ranked.filter { it.stats.reachable }.distinctBy { it.node.region.key }
}
