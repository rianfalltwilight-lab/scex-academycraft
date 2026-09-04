package com.mohistmc.academy.energy.impl;

import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.VBlocks.VNGenerator;
import com.mohistmc.academy.energy.impl.VBlocks.VNReceiver;
import com.mohistmc.academy.energy.impl.VBlocks.VWNode;
import com.mohistmc.academy.energy.api.block.IWirelessUser;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.WirelessEvents;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 无线能源系统入口 —— 监听服务端 tick，驱动所有网络和节点连接的更新。
 */
@EventBusSubscriber
public class WirelessSystem {

    public enum MatrixNetworkState {
        INACTIVE,
        PRESENT,
        RECOVERED,
        NEEDS_REINITIALIZATION,
        RECOVERY_FAILED;

        public boolean active() {
            return this == PRESENT || this == RECOVERED;
        }
    }

    private WirelessSystem() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            WiWorldData data = WiWorldData.getNonCreate(level);
            if (data != null) {
                data.level = level;
                data.tick();
            }
        }
    }

    // ==================== 便捷方法 ====================

    /** 创建无线网络 */
    public static boolean createNetwork(ServerLevel level,
                                         com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix,
                                         String ssid, String password) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity) || ssid == null || password == null
                || ssid.length() > 64 || password.length() > 64) return false;
        WirelessEvents.Create event = new WirelessEvents.Create(matrix, ssid, password);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;
        WiWorldData data = WiWorldData.get(level);
        return data.createNetwork(matrix, ssid, password);
    }

    /** Roll back a just-created network when matrix initialization cannot commit. */
    public static boolean removeNetwork(ServerLevel level,
                                        com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity)) return false;
        WiWorldData data = WiWorldData.getNonCreate(level);
        boolean removed = data != null && data.removeNetwork(matrix);
        if (removed) NeoForge.EVENT_BUS.post(new WirelessEvents.Destroy(matrix));
        return removed;
    }

    /** 节点加入网络 */
    public static boolean linkNode(ServerLevel level,
                                    com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix,
                                    com.mohistmc.academy.energy.api.block.IWirelessNode node,
                                    String password) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || password == null || password.length() > 64) return false;
        WirelessEvents.LinkNode event = new WirelessEvents.LinkNode(node, matrix, password);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;
        WiWorldData data = WiWorldData.get(level);
        if (matrix instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity be
                && !reconcileMatrixNetwork(level, be).active()) return false;
        WirelessNet net = data.getNetwork(matrix);
        if (net == null) return false;
        boolean linked = net.addNode(new VWNode(node), password);
        if (linked) refreshNodeState(node);
        return linked;
    }

    public static boolean unlinkNode(ServerLevel level,
                                     com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix,
                                     com.mohistmc.academy.energy.api.block.IWirelessNode node) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)) return false;
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null) return false;
        WirelessNet matrixNet = data.getNetwork(matrix);
        WirelessNet nodeNet = data.getNetwork(node);
        if (matrixNet == null || matrixNet != nodeNet) return false;
        matrixNet.removeNodeImmediately(new VWNode(node));
        data.setDirty();
        refreshNodeState(node);
        NeoForge.EVENT_BUS.post(new WirelessEvents.UnlinkNode(node, matrix));
        return true;
    }

    /** 发电机加入节点 */
    public static boolean linkGenerator(ServerLevel level,
                                         com.mohistmc.academy.energy.api.block.IWirelessNode node,
                                         IWirelessGenerator gen,
                                         boolean needAuth, String password) {
        if (level == null || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || !(gen instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || password == null || password.length() > 64) return false;
        WirelessEvents.LinkUser event = new WirelessEvents.LinkUser(gen, node, needAuth, password);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;
        WiWorldData data = WiWorldData.get(level);
        if (needAuth) {
            if (!passwordMatches(node.getPassword(), password)) return false;
        }
        NodeConn conn = data.getNodeConnection(node);
        if (conn == null) return false;
        boolean linked = conn.addGenerator(new VNGenerator(gen));
        if (linked) refreshNodeState(node);
        return linked;
    }

    /** 接收器加入节点 */
    public static boolean linkReceiver(ServerLevel level,
                                        com.mohistmc.academy.energy.api.block.IWirelessNode node,
                                        IWirelessReceiver rec,
                                        boolean needAuth, String password) {
        if (level == null || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || !(rec instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || password == null || password.length() > 64) return false;
        WirelessEvents.LinkUser event = new WirelessEvents.LinkUser(rec, node, needAuth, password);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return false;
        WiWorldData data = WiWorldData.get(level);
        if (needAuth) {
            if (!passwordMatches(node.getPassword(), password)) return false;
        }
        NodeConn conn = data.getNodeConnection(node);
        if (conn == null) return false;
        boolean linked = conn.addReceiver(new VNReceiver(rec));
        if (linked) refreshNodeState(node);
        return linked;
    }

    /**
     * Server-authoritative unlink for one machine.  This is intentionally
     * separate from NodeConn.dispose(): disposing a connection would disconnect
     * every generator/receiver sharing the same node.
     */
    public static boolean unlinkUser(ServerLevel level, IWirelessUser user) {
        if (level == null || !(user instanceof net.minecraft.world.level.block.entity.BlockEntity)) {
            return false;
        }
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null) return false;
        NodeConn conn = data.getNodeConnection(user);
        if (conn == null) return false;

        boolean removed;
        if (user instanceof IWirelessGenerator generator) {
            removed = conn.unlinkGenerator(new VNGenerator(generator));
        } else if (user instanceof IWirelessReceiver receiver) {
            removed = conn.unlinkReceiver(new VNReceiver(receiver));
        } else {
            removed = false;
        }
        if (removed) {
            data.setDirty();
            // The node itself is resolved through the connection before
            // unlinking; refresh it after the user is removed so its
            // connected/working model state is not one tick behind.
            var node = conn.getNode();
            refreshNodeState(node);
            NeoForge.EVENT_BUS.post(new WirelessEvents.UnlinkUser(user));
        }
        return removed;
    }

    /**
     * Remove every coordinate-based relationship owned by a node while its
     * block entity is still present.  Waiting for the next SavedData tick lets
     * a node replaced at the same position inherit the old NodeConn and matrix
     * membership because virtual blocks deliberately use stable coordinates.
     */
    public static boolean detachNodeOnRemoval(ServerLevel level, IWirelessNode node) {
        if (level == null || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)) {
            return false;
        }
        boolean changed = false;
        WiWorldData data = WiWorldData.getNonCreate(level);
        WirelessNet network = data == null ? null : data.getNetwork(node);
        if (network != null) {
            var matrix = network.getMatrix();
            // The owning Matrix may be in an unloaded chunk.  Removal must
            // still revoke the coordinate lookup immediately or a replacement
            // node at this position inherits membership until that Matrix is
            // loaded again.  Post the rich event only when its Matrix endpoint
            // can be resolved.
            network.removeNodeImmediately(new VWNode(node));
            data.setDirty();
            changed = true;
            if (matrix != null) {
                NeoForge.EVENT_BUS.post(new WirelessEvents.UnlinkNode(node, matrix));
            }
        }
        if (data != null) {
            NodeConn connection = data.getExistingNodeConnection(node);
            if (connection != null && !connection.isDisposed()) {
                // validate() immediately rejects a disposed connection.  Its
                // conditional cleanup on the next tick cannot erase a newly
                // placed node's replacement mapping.
                connection.dispose();
                data.setDirty();
                changed = true;
            }
        }
        refreshNodeState(node);
        return changed;
    }

    /** 获取节点连接信息 */
    public static NodeConn getNodeConnection(ServerLevel level,
                                               com.mohistmc.academy.energy.api.block.IWirelessNode node) {
        if (level == null || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)) return null;
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null) return null;
        return data.getNodeConnection(node);
    }

    /** Read-only lookup used by the legacy developer status panel. */
    public static NodeConn getUserConnection(ServerLevel level,
                                               com.mohistmc.academy.energy.api.block.IWirelessUser user) {
        if (level == null || !(user instanceof net.minecraft.world.level.block.entity.BlockEntity)) return null;
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null) return null;
        return data.getNodeConnection(user);
    }

    /** 获取节点所属网络 */
    public static WirelessNet getNetwork(ServerLevel level,
                                           com.mohistmc.academy.energy.api.block.IWirelessNode node) {
        if (level == null || !(node instanceof net.minecraft.world.level.block.entity.BlockEntity)) return null;
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null) return null;
        return data.getNetwork(node);
    }

    /**
     * Reconciles the Matrix block-entity flag with dimension SavedData.
     *
     * Older rebuilt jars could persist {@code initialized=true} without a
     * corresponding WirelessNet.  That state could neither link nodes nor be
     * initialized again.  Recovery is server-only, idempotent, never mutates
     * component slots, and reuses an existing network instead of replacing it.
     */
    public static MatrixNetworkState reconcileMatrixNetwork(ServerLevel level,
            com.mohistmc.academy.world.block.entity.MatrixBlockEntity matrix) {
        if (level == null || matrix == null || matrix.getLevel() != level
                || level.getBlockEntity(matrix.getBlockPos()) != matrix) {
            return MatrixNetworkState.RECOVERY_FAILED;
        }

        WiWorldData existingData = WiWorldData.getNonCreate(level);
        WirelessNet existing = existingData == null ? null : existingData.getNetwork(matrix);
        if (existing != null) {
            boolean changed = false;
            if (!matrix.isInitialized()) {
                matrix.setInitialized(true);
                changed = true;
            }
            if (!existing.getSSID().equals(matrix.getSSID())) {
                matrix.setSSID(existing.getSSID());
                changed = true;
            }
            if (!existing.getPassword().equals(matrix.getPassword())) {
                matrix.setPassword(existing.getPassword());
                changed = true;
            }
            if (changed) syncMatrix(level, matrix);
            return MatrixNetworkState.PRESENT;
        }

        if (!matrix.isInitialized()) return MatrixNetworkState.INACTIVE;

        String ssid = matrix.getSSID();
        String password = matrix.getPassword();
        if (!matrix.hasInitializationMaterials() || ssid == null || ssid.isBlank()
                || ssid.length() > 64 || password == null || password.length() > 64) {
            // No authoritative network exists, so clearing only the stale flag
            // cannot destroy energy or node membership.  It restores the normal
            // INIT path without consuming or duplicating the installed parts.
            matrix.setInitialized(false);
            syncMatrix(level, matrix);
            return MatrixNetworkState.NEEDS_REINITIALIZATION;
        }

        if (!createNetwork(level, matrix, ssid, password)) {
            return MatrixNetworkState.RECOVERY_FAILED;
        }
        syncMatrix(level, matrix);
        return MatrixNetworkState.RECOVERED;
    }

    private static void syncMatrix(ServerLevel level,
            com.mohistmc.academy.world.block.entity.MatrixBlockEntity matrix) {
        matrix.setChanged();
        level.sendBlockUpdated(matrix.getBlockPos(), matrix.getBlockState(), matrix.getBlockState(), 3);
    }

    private static boolean passwordMatches(String expected, String supplied) {
        if (expected == null || supplied == null || expected.length() > 64 || supplied.length() > 64) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static void refreshNodeState(IWirelessNode node) {
        if (node instanceof com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity be) {
            be.refreshConnectionState();
        }
    }

    /** Server-authoritative password mutation boundary for integrations and GUIs. */
    public static boolean changeSSID(ServerLevel level,
                                     com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix,
                                     String ssid) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || ssid == null || ssid.isBlank() || ssid.length() > 32) return false;
        WiWorldData data = WiWorldData.getNonCreate(level);
        WirelessNet net = data == null ? null : data.getNetwork(matrix);
        if (net == null) return false;
        net.setSSID(ssid);
        if (matrix instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity be) {
            be.setSSID(ssid);
            syncMatrix(level, be);
        }
        data.setDirty();
        return true;
    }

    /** Server-authoritative password mutation boundary for integrations and GUIs. */
    public static boolean changePassword(ServerLevel level,
                                         com.mohistmc.academy.energy.api.block.IWirelessMatrix matrix,
                                         String password) {
        if (level == null || !(matrix instanceof net.minecraft.world.level.block.entity.BlockEntity)
                || password == null || password.length() > 64) return false;
        WirelessNet net = WiWorldData.getNonCreate(level) == null ? null : WiWorldData.getNonCreate(level).getNetwork(matrix);
        if (net == null) return false;
        WirelessEvents.ChangePassword event = new WirelessEvents.ChangePassword(matrix, password);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || !net.resetPassword(password)) return false;
        if (matrix instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity be) be.setPassword(password);
        WiWorldData.getNonCreate(level).setDirty();
        return true;
    }
}
