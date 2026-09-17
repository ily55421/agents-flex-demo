/**
 * 轻量力导向布局：斥力（O(n²)，几百节点规模足够）+ 弹簧 + 向心引力。
 * 世界坐标以 (0,0) 为中心；alpha 随迭代衰减，稳定后停止计算，
 * 过滤条件变化时调 reheat() 重新布局。
 */
export interface SimNode {
    id: string
    x: number
    y: number
    vx: number
    vy: number
    /** 固定位置（用户拖拽中）。 */
    fixed: boolean
    /** 连接度，用于节点尺寸。 */
    deg: number
}

export interface SimEdge {
    a: SimNode
    b: SimNode
}

export class ForceLayout {
    private nodes: SimNode[]
    private edges: SimEdge[]
    alpha = 1

    constructor(nodes: SimNode[], edges: SimEdge[]) {
        this.nodes = nodes
        this.edges = edges
    }

    reheat(alpha = 1) {
        this.alpha = alpha
    }

    /** 推进一次模拟；返回当前 alpha（0 表示已稳定）。 */
    tick(): number {
        if (this.alpha < 0.005) return 0
        const nodes = this.nodes
        const alpha = this.alpha
        // 斥力：库仑模型，按距离平方衰减，超过截止距离跳过
        for (let i = 0; i < nodes.length; i++) {
            const a = nodes[i]
            for (let j = i + 1; j < nodes.length; j++) {
                const b = nodes[j]
                let dx = b.x - a.x
                let dy = b.y - a.y
                let d2 = dx * dx + dy * dy
                if (d2 > 2250000) continue // 1500px 外忽略
                if (d2 < 1) {
                    dx = (Math.random() - 0.5) * 2
                    dy = (Math.random() - 0.5) * 2
                    d2 = 4
                }
                const d = Math.sqrt(d2)
                const repulse = (4200 * alpha) / d2
                const fx = (dx / d) * repulse
                const fy = (dy / d) * repulse
                a.vx -= fx
                a.vy -= fy
                b.vx += fx
                b.vy += fy
            }
        }
        // 弹簧：相邻节点拉近到理想距离
        for (const edge of this.edges) {
            const a = edge.a
            const b = edge.b
            const dx = b.x - a.x
            const dy = b.y - a.y
            const d = Math.sqrt(dx * dx + dy * dy) || 1
            const target = 90
            const force = ((d - target) / d) * 0.035 * alpha
            const fx = dx * force
            const fy = dy * force
            a.vx += fx
            a.vy += fy
            b.vx -= fx
            b.vy -= fy
        }
        // 向心 + 阻尼积分
        for (const node of this.nodes) {
            node.vx -= node.x * 0.0035 * alpha
            node.vy -= node.y * 0.0035 * alpha
            if (node.fixed) {
                node.vx = 0
                node.vy = 0
                continue
            }
            node.vx *= 0.86
            node.vy *= 0.86
            node.x += Math.max(-32, Math.min(32, node.vx))
            node.y += Math.max(-32, Math.min(32, node.vy))
        }
        this.alpha *= 0.985
        return this.alpha
    }

    /** 以连通分量为单位把节点散布在初始圆环上，减少初始纠缠。 */
    static initialPositions(nodes: SimNode[], edges: SimEdge[], radius = 340) {
        const adjacency = new Map<string, string[]>()
        for (const edge of edges) {
            if (!adjacency.has(edge.a.id)) adjacency.set(edge.a.id, [])
            if (!adjacency.has(edge.b.id)) adjacency.set(edge.b.id, [])
            adjacency.get(edge.a.id)!.push(edge.b.id)
            adjacency.get(edge.b.id)!.push(edge.a.id)
        }
        const visited = new Set<string>()
        let componentIndex = 0
        for (const node of nodes) {
            if (visited.has(node.id)) continue
            const queue = [node.id]
            visited.add(node.id)
            const members: SimNode[] = []
            const byId = new Map(nodes.map(item => [item.id, item]))
            while (queue.length) {
                const current = queue.shift()!
                const member = byId.get(current)
                if (member) members.push(member)
                for (const peer of adjacency.get(current) ?? []) {
                    if (!visited.has(peer)) {
                        visited.add(peer)
                        queue.push(peer)
                    }
                }
            }
            const angle = componentIndex * 2.399963 // 黄金角散布连通分量
            const cx = Math.cos(angle) * radius
            const cy = Math.sin(angle) * radius
            members.forEach((member, index) => {
                const ring = radius * (0.25 + 0.75 * (index / Math.max(members.length, 1)))
                const spread = (index % 12) / 12 * Math.PI * 2
                member.x = cx + Math.cos(spread) * ring * 0.35 + (Math.random() - 0.5) * 24
                member.y = cy + Math.sin(spread) * ring * 0.35 + (Math.random() - 0.5) * 24
            })
            componentIndex++
        }
    }

    /**
     * 层次树布局：按 parent 深度分行、行内均匀展开。
     * 适合本体 TBox 这类小型层级图（静态、无叠压、可读性优先）。
     *
     * @param nodes   节点（直接写入 x/y）
     * @param parents id → 父 id（根节点无父）
     * @param rowGap  行距
     */
    static treeLayout(nodes: SimNode[], parents: Map<string, string | null>, rowGap = 120) {
        const depthOf = new Map<string, number>()
        const depth = (id: string, guard = 0): number => {
            if (depthOf.has(id)) return depthOf.get(id)!
            if (guard > nodes.length) return 0
            const parent = parents.get(id)
            const value = parent && parents.has(parent) ? depth(parent, guard + 1) + 1 : 0
            depthOf.set(id, value)
            return value
        }
        for (const node of nodes) depth(node.id)

        const rows = new Map<number, SimNode[]>()
        for (const node of nodes) {
            const level = depthOf.get(node.id) ?? 0
            if (!rows.has(level)) rows.set(level, [])
            rows.get(level)!.push(node)
        }
        const maxDepth = Math.max(...rows.keys(), 0)
        for (const [level, members] of rows) {
            members.sort((a, b) => a.id.localeCompare(b.id))
            members.forEach((node, index) => {
                node.x = (index + 0.5 - members.length / 2) * 150
                node.y = (level - maxDepth / 2) * rowGap
            })
        }
    }
}
