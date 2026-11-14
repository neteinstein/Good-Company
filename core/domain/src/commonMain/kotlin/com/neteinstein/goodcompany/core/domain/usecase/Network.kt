package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What an edge on the network map stands for (legend: Family, Partner, Friend, Introduced). */
enum class EdgeKind { FAMILY, PARTNER, FRIEND, INTRODUCED }

/** One person on the map. [circle] picks the colour (the first circle they're in); [warmth] is null when hidden. */
data class NetworkNode(val friend: Friend, val circle: FriendGroup?, val circleIds: Set<String>, val warmth: Int?)

/** An edge between two people; [a] or [b] is [NetworkGraph.ME] for an edge to you. */
data class NetworkEdge(val a: String, val b: String, val kind: EdgeKind)

/**
 * The network map (issue #46, F6): you at the centre, people as nodes, edges from links between people (#39), from
 * "introduced by" and from what each person is to you. Co-presence at meetups is not an edge (plan question 3).
 */
data class NetworkGraph(val nodes: List<NetworkNode>, val edges: List<NetworkEdge>, val circles: List<FriendGroup>) {
    companion object {
        /** Your node's id (never a friend id: those are UUIDs, contact ids or `sample-…`). */
        const val ME = "me"

        /**
         * Who is on the map: everyone in "your people" ([PeopleDashboard.isYours]) plus anyone a link or an
         * "introduced by" ties to them. People who are only in the address book stay off it.
         */
        fun build(
            friends: List<Friend>,
            links: List<PersonLink>,
            circles: List<FriendGroup>,
            warmth: Map<FriendId, Int>?,
        ): NetworkGraph {
            val byId = friends.associateBy { it.id }
            val memberOf = circles.flatMap { c -> c.memberIds.map { it to c } }.groupBy({ it.first }, { it.second })
            val core = friends.filter { PeopleDashboard.isYours(it, memberOf.containsKey(it.id)) }.mapTo(LinkedHashSet()) { it.id }
            val edges = LinkedHashMap<Pair<String, String>, NetworkEdge>()
            fun add(x: String, y: String, kind: EdgeKind) {
                if (x == y) return
                val key = if (x < y) x to y else y to x
                // Stronger kinds win when two facts join the same pair (family > partner > friend > introduced).
                val old = edges[key]
                if (old == null || kind.ordinal < old.kind.ordinal) edges[key] = NetworkEdge(key.first, key.second, kind)
            }
            val included = LinkedHashSet(core)
            links.filter { byId.containsKey(it.from) && byId.containsKey(it.to) && (it.from in core || it.to in core) }.forEach { l ->
                included += l.from
                included += l.to
                add(l.from.value, l.to.value, kindOf(l))
            }
            friends.forEach { f ->
                val by = f.introducedBy?.friendId ?: return@forEach
                if (byId.containsKey(by) && (f.id in core || by in core)) {
                    included += f.id
                    included += by
                    add(f.id.value, by.value, EdgeKind.INTRODUCED)
                }
            }
            // You are tied to your own people; people only on the map through someone else hang off that person.
            core.forEach { id -> add(ME, id.value, kindToMe(byId.getValue(id).relationToMe)) }
            val nodes = included.mapNotNull(byId::get).map { f ->
                val cs = memberOf[f.id].orEmpty()
                NetworkNode(f, cs.firstOrNull(), cs.mapTo(HashSet()) { it.id }, warmth?.get(f.id))
            }
            return NetworkGraph(nodes, edges.values.toList(), circles.filter { c -> c.memberIds.any { it in included } })
        }

        private fun kindOf(link: PersonLink): EdgeKind = when {
            link.type.category == LinkCategory.FAMILY -> EdgeKind.FAMILY
            link.isCurrentRomance -> EdgeKind.PARTNER
            else -> EdgeKind.FRIEND
        }

        private fun kindToMe(relation: LinkType?): EdgeKind = when {
            relation == null -> EdgeKind.FRIEND
            relation.isFamily -> EdgeKind.FAMILY
            relation.isRomantic && relation != LinkType.EX -> EdgeKind.PARTNER
            else -> EdgeKind.FRIEND
        }
    }
}

/** A point in layout space (unitless; about [ForceLayout.IDEAL_LENGTH] between linked people). */
data class Point(val x: Double, val y: Double)

/**
 * A small force-directed layout (Fruchterman–Reingold) for the network map, so no graph library is needed. Pure and
 * deterministic: the same graph and [previous] positions always give the same result (no randomness; new nodes start
 * on a golden-angle spiral). [pinned] stays put (you, at the origin). A fixed iteration budget keeps it fast for a few
 * hundred people; [previous] positions are kept as the start, so a re-layout after a small change barely moves.
 */
object ForceLayout {
    const val IDEAL_LENGTH = 100.0
    const val ITERATIONS = 300

    /** A light pull to the centre so separate groups don't drift away. */
    private const val GRAVITY = 0.02
    private const val MIN_DISTANCE = 0.01
    private const val GOLDEN_ANGLE = 2.399963229728653

    fun layout(
        ids: List<String>,
        edges: List<Pair<String, String>>,
        pinned: Map<String, Point> = emptyMap(),
        previous: Map<String, Point> = emptyMap(),
        iterations: Int = ITERATIONS,
    ): Map<String, Point> {
        val n = ids.size
        if (n == 0) return emptyMap()
        val index = ids.withIndex().associate { (i, id) -> id to i }
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        val fixed = BooleanArray(n)
        ids.forEachIndexed { i, id ->
            val p = pinned[id] ?: previous[id] ?: spiral(i)
            xs[i] = p.x
            ys[i] = p.y
            fixed[i] = id in pinned
        }
        val pairs = edges.mapNotNull { (a, b) -> index[a]?.let { i -> index[b]?.let { j -> if (i != j) i to j else null } } }
        val k = IDEAL_LENGTH
        val start = if (previous.isEmpty()) k * sqrt(n.toDouble()) / 2 else k / 4
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        repeat(iterations) { step ->
            dx.fill(0.0)
            dy.fill(0.0)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var ddx = xs[i] - xs[j]
                    var ddy = ys[i] - ys[j]
                    var d = sqrt(ddx * ddx + ddy * ddy)
                    if (d < MIN_DISTANCE) {
                        // Same spot: push apart along a fixed, index-based direction (deterministic).
                        ddx = cos(i.toDouble() - j)
                        ddy = sin(i.toDouble() - j)
                        d = MIN_DISTANCE
                    }
                    val force = k * k / d
                    val fx = ddx / d * force
                    val fy = ddy / d * force
                    dx[i] += fx
                    dy[i] += fy
                    dx[j] -= fx
                    dy[j] -= fy
                }
            }
            for ((i, j) in pairs) {
                val ddx = xs[i] - xs[j]
                val ddy = ys[i] - ys[j]
                val d = max(sqrt(ddx * ddx + ddy * ddy), MIN_DISTANCE)
                val force = d * d / k
                val fx = ddx / d * force
                val fy = ddy / d * force
                dx[i] -= fx
                dy[i] -= fy
                dx[j] += fx
                dy[j] += fy
            }
            val temperature = start * (1.0 - step.toDouble() / iterations)
            for (i in 0 until n) {
                if (fixed[i]) continue
                dx[i] -= xs[i] * GRAVITY * k
                dy[i] -= ys[i] * GRAVITY * k
                val d = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
                if (d > 0) {
                    val move = min(d, temperature)
                    xs[i] += dx[i] / d * move
                    ys[i] += dy[i] / d * move
                }
            }
        }
        return ids.withIndex().associate { (i, id) -> id to Point(xs[i], ys[i]) }
    }

    private fun spiral(i: Int): Point {
        val r = IDEAL_LENGTH * sqrt(i + 1.0)
        val a = i * GOLDEN_ANGLE
        return Point(r * cos(a), r * sin(a))
    }
}

/** The network map: the graph and where each node goes (you at the origin). */
data class NetworkMap(val graph: NetworkGraph, val positions: Map<String, Point>, val showWarmth: Boolean)

/**
 * The network map's data. The layout is cached between emissions: positions of people already placed are the start
 * of the next layout, so the map doesn't jump when something small changes. Run it off the main thread.
 */
class ObserveNetworkMapUseCase(
    private val friends: FriendRepository,
    private val links: PersonLinkRepository,
    private val groups: GroupRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<NetworkMap> {
        var cache: Map<String, Point> = emptyMap()
        var cacheKey: Pair<Set<String>, Set<NetworkEdge>>? = null
        return combine(
            combine(friends.friends, links.links, groups.groups, ::Triple),
            hangouts.hangouts,
            interactions.interactions,
            settings.settings,
        ) { (fs, ls, gs), hs, ints, s ->
            val rules = CadenceRules.from(s)
            val today = clock.today()
            val warmth = if (s.showWarmth) {
                fs.associate { it.id to Warmth.score(it, hs, ints, rules.cadenceFor(it), today, clock.timeZone).value }
            } else {
                null
            }
            val graph = NetworkGraph.build(fs, ls, gs, warmth)
            val ids = listOf(NetworkGraph.ME) + graph.nodes.map { it.friend.id.value }
            val key = ids.toSet() to graph.edges.toSet()
            if (key != cacheKey) {
                val iterations = if (cache.isEmpty()) ForceLayout.ITERATIONS else ForceLayout.ITERATIONS / 3
                cache = ForceLayout.layout(
                    ids,
                    graph.edges.map { it.a to it.b },
                    pinned = mapOf(NetworkGraph.ME to Point(0.0, 0.0)),
                    previous = cache,
                    iterations = iterations,
                )
                cacheKey = key
            }
            NetworkMap(graph, cache, s.showWarmth)
        }
    }
}
