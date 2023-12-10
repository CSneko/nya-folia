package net.minecraft.world.level.pathfinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Path {
    public final List<Node> nodes;
    @Nullable
    private Path.DebugData debugData;
    private int nextNodeIndex;
    private final BlockPos target;
    private final float distToTarget;
    private final boolean reached;
    public boolean hasNext() { return getNextNodeIndex() < this.nodes.size(); } // Paper

    public Path(List<Node> nodes, BlockPos target, boolean reachesTarget) {
        this.nodes = nodes;
        this.target = target;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : this.nodes.get(this.nodes.size() - 1).distanceManhattan(this.target);
        this.reached = reachesTarget;
    }

    public void advance() {
        ++this.nextNodeIndex;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    @Nullable
    public Node getEndNode() {
        return !this.nodes.isEmpty() ? this.nodes.get(this.nodes.size() - 1) : null;
    }

    public Node getNode(int index) {
        return this.nodes.get(index);
    }

    public void truncateNodes(int length) {
        if (this.nodes.size() > length) {
            this.nodes.subList(length, this.nodes.size()).clear();
        }

    }

    public void replaceNode(int index, Node node) {
        this.nodes.set(index, node);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(int nodeIndex) {
        this.nextNodeIndex = nodeIndex;
    }

    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        Node node = this.nodes.get(index);
        double d = (double)node.x + (double)((int)(entity.getBbWidth() + 1.0F)) * 0.5D;
        double e = (double)node.y;
        double f = (double)node.z + (double)((int)(entity.getBbWidth() + 1.0F)) * 0.5D;
        return new Vec3(d, e, f);
    }

    public BlockPos getNodePos(int index) {
        return this.nodes.get(index).asBlockPos();
    }

    public Vec3 getNextEntityPos(Entity entity) {
        return this.getEntityPosAtNode(entity, this.nextNodeIndex);
    }

    public BlockPos getNextNodePos() {
        return this.nodes.get(this.nextNodeIndex).asBlockPos();
    }

    public Node getNextNode() {
        return this.nodes.get(this.nextNodeIndex);
    }

    @Nullable
    public Node getPreviousNode() {
        return this.nextNodeIndex > 0 ? this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    public boolean sameAs(@Nullable Path o) {
        if (o == null) {
            return false;
        } else if (o.nodes.size() != this.nodes.size()) {
            return false;
        } else {
            for(int i = 0; i < this.nodes.size(); ++i) {
                Node node = this.nodes.get(i);
                Node node2 = o.nodes.get(i);
                if (node.x != node2.x || node.y != node2.y || node.z != node2.z) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean canReach() {
        return this.reached;
    }

    @VisibleForDebug
    void setDebug(Node[] debugNodes, Node[] debugSecondNodes, Set<Target> debugTargetNodes) {
        this.debugData = new Path.DebugData(debugNodes, debugSecondNodes, debugTargetNodes);
    }

    @Nullable
    public Path.DebugData debugData() {
        return this.debugData;
    }

    public void writeToStream(FriendlyByteBuf buf) {
        if (this.debugData != null && !this.debugData.targetNodes.isEmpty()) {
            buf.writeBoolean(this.reached);
            buf.writeInt(this.nextNodeIndex);
            buf.writeBlockPos(this.target);
            buf.writeCollection(this.nodes, (bufx, node) -> {
                node.writeToStream(bufx);
            });
            this.debugData.write(buf);
        }
    }

    public static Path createFromStream(FriendlyByteBuf buf) {
        boolean bl = buf.readBoolean();
        int i = buf.readInt();
        BlockPos blockPos = buf.readBlockPos();
        List<Node> list = buf.readList(Node::createFromStream);
        Path.DebugData debugData = Path.DebugData.read(buf);
        Path path = new Path(list, blockPos, bl);
        path.debugData = debugData;
        path.nextNodeIndex = i;
        return path;
    }

    @Override
    public String toString() {
        return "Path(length=" + this.nodes.size() + ")";
    }

    public BlockPos getTarget() {
        return this.target;
    }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    static Node[] readNodeArray(FriendlyByteBuf buf) {
        Node[] nodes = new Node[buf.readVarInt()];

        for(int i = 0; i < nodes.length; ++i) {
            nodes[i] = Node.createFromStream(buf);
        }

        return nodes;
    }

    static void writeNodeArray(FriendlyByteBuf buf, Node[] nodes) {
        buf.writeVarInt(nodes.length);

        for(Node node : nodes) {
            node.writeToStream(buf);
        }

    }

    public Path copy() {
        Path path = new Path(this.nodes, this.target, this.reached);
        path.debugData = this.debugData;
        path.nextNodeIndex = this.nextNodeIndex;
        return path;
    }

    public static record DebugData(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeCollection(this.targetNodes, (bufx, node) -> {
                node.writeToStream(bufx);
            });
            Path.writeNodeArray(buf, this.openSet);
            Path.writeNodeArray(buf, this.closedSet);
        }

        public static Path.DebugData read(FriendlyByteBuf buf) {
            HashSet<Target> hashSet = buf.readCollection(HashSet::new, Target::createFromStream);
            Node[] nodes = Path.readNodeArray(buf);
            Node[] nodes2 = Path.readNodeArray(buf);
            return new Path.DebugData(nodes, nodes2, hashSet);
        }
    }
}
