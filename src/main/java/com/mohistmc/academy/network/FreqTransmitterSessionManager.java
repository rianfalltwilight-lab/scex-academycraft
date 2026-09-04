package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessMatrix;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.terminal.AppRegistry;
import com.mohistmc.academy.world.block.IDevStructure;
import com.mohistmc.academy.world.block.IDevSubStructure;
import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.block.MatrixSubBlock;
import com.mohistmc.academy.world.block.WindGenBase;
import com.mohistmc.academy.world.block.WindGenBaseSubBlock;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-owned frequency-transmitter workflow.
 *
 * <p>The client never submits a source or target coordinate.  Selection is
 * driven by NeoForge's server-side {@link PlayerInteractEvent.RightClickBlock},
 * after vanilla has already accepted a real block interaction.  The only C2S
 * values are an opaque nonce and a bounded password for the already selected
 * source.  This preserves the two-stage 1.0.7 interaction while preventing a
 * custom payload from targeting an unloaded, remote, or cross-dimensional
 * block.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class FreqTransmitterSessionManager {
    public enum Stage { SELECT_SOURCE, AUTHORIZE_SOURCE, SELECT_TARGET }
    public enum SourceKind { NONE, MATRIX, NODE }

    public enum SelectionResult {
        NO_SESSION,
        INVALID_TARGET,
        PASSWORD_REQUIRED,
        SOURCE_ACCEPTED,
        LINKED,
        LINK_FAILED,
        CLOSED
    }

    private static final long SESSION_TTL_TICKS = 20L * 20L;
    /** Legacy FreqTransmitterUI used a fixed four-block crosshair ray. */
    private static final double MAX_PLAYER_SELECT_DISTANCE_SQR = 4.0 * 4.0;
    /** Main origins can be up to a 2x2x2 proxy footprint away from the clicked part. */
    private static final double MAX_CANONICAL_REVALIDATE_DISTANCE_SQR = 6.0 * 6.0;
    private static final int MAX_AUTH_FAILURES = 5;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private record Session(UUID nonce, ResourceKey<Level> dimension, Stage stage,
                           SourceKind kind, BlockPos source, BlockEntity sourceIdentity,
                           String authFingerprint, long expiresAt,
                           int authFailures, long retryAt) {
        Session refresh(long now) {
            return new Session(nonce, dimension, stage, kind, source, sourceIdentity,
                    authFingerprint,
                    now + SESSION_TTL_TICKS, authFailures, retryAt);
        }

        Session transition(Stage next, SourceKind nextKind, BlockPos nextSource,
                           BlockEntity nextIdentity, String nextAuthFingerprint, long now) {
            return new Session(nonce, dimension, next, nextKind,
                    nextSource == null ? null : nextSource.immutable(),
                    nextIdentity, nextAuthFingerprint,
                    now + SESSION_TTL_TICKS, 0, 0);
        }

        Session failedAuth(long now) {
            int failures = Math.min(MAX_AUTH_FAILURES, authFailures + 1);
            long delay = Math.min(20L << Math.min(failures - 1, 4), 20L * 15L);
            return new Session(nonce, dimension, stage, kind, source, sourceIdentity,
                    authFingerprint,
                    now + SESSION_TTL_TICKS, failures, now + delay);
        }
    }

    private record Resolved(BlockPos pos, BlockEntity blockEntity) {}

    private FreqTransmitterSessionManager() {}

    /** Opens or replaces this player's transmitter session. */
    public static UUID open(ServerPlayer player) {
        return open(player, UUID.randomUUID());
    }

    /**
     * Opens a session using the client's one-shot request nonce.  A client
     * chosen nonce grants no authority (sessions remain scoped to the sending
     * player), but lets an Esc/cancel packet unambiguously close an OPEN that
     * is still in flight on the same ordered connection.
     */
    public static UUID open(ServerPlayer player, UUID requestedNonce) {
        if (!hasTransmitter(player)) {
            SESSIONS.remove(player.getUUID());
            closeWithMessage(player, requestedNonce, "未安装频率变送器 APP");
            return null;
        }
        if (requestedNonce == null || requestedNonce.equals(new UUID(0, 0))) {
            closeWithMessage(player, requestedNonce, "无效的频率变送器会话");
            return null;
        }
        long now = player.serverLevel().getGameTime();
        UUID nonce = requestedNonce;
        Session session = new Session(nonce, player.level().dimension(), Stage.SELECT_SOURCE,
                SourceKind.NONE, null, null, null, now + SESSION_TTL_TICKS, 0, 0);
        SESSIONS.put(player.getUUID(), session);
        sendState(player, session, FreqTransmitterStatePacket.SELECT_SOURCE, "",
                "右击一个无线矩阵或无线节点");
        return nonce;
    }

    /** Password authorization for the exact source captured by a real block interaction. */
    public static boolean authorize(ServerPlayer player, UUID nonce, String password) {
        if (player == null || nonce == null || password == null
                || password.length() > NetworkInputLimits.PASSWORD) return false;
        Session session = active(player, nonce, true);
        if (session == null || session.stage != Stage.AUTHORIZE_SOURCE) return false;
        long now = player.serverLevel().getGameTime();
        if (now < session.retryAt) {
            sendState(player, session, FreqTransmitterStatePacket.PASSWORD_REQUIRED,
                    sourceLabel(player.serverLevel(), session), "尝试过快，请稍后再试");
            return false;
        }

        Resolved source = resolveCanonical(player.serverLevel(), session.source);
        if (!validStoredSource(player.serverLevel(), session, source)
                || !canPlayerOperateCanonical(player, source.pos)) {
            terminate(player, session, "源设备已失效或不再可操作");
            return false;
        }

        String expected = session.kind == SourceKind.MATRIX
                ? ((MatrixBlockEntity) source.blockEntity).getPassword()
                : ((IWirelessNode) source.blockEntity).getPassword();
        if (!passwordMatches(expected, password)) {
            // 1.0.7 closes the transmitter after a failed authorization.  A
            // fresh OPEN is rate-limited, so this also avoids keeping a
            // reusable password-oracle session alive.
            terminate(player, session.failedAuth(now), "密码错误，会话已关闭");
            return false;
        }

        Session authorized = session.transition(Stage.SELECT_TARGET, session.kind, session.source,
                session.sourceIdentity, credentialFingerprint(expected), now);
        SESSIONS.put(player.getUUID(), authorized);
        sendState(player, authorized, FreqTransmitterStatePacket.SELECT_TARGET,
                sourceLabel(player.serverLevel(), authorized), targetPrompt(authorized.kind));
        return true;
    }

    /** Cancel only the nonce the client actually owns; stale close packets cannot kill a replacement. */
    public static boolean cancel(ServerPlayer player, UUID nonce) {
        if (player == null || nonce == null) return false;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.nonce.equals(nonce)) return false;
        if (!SESSIONS.remove(player.getUUID(), session)) return false;
        sendClosed(player, session.nonce, "频率变送器会话已关闭");
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session raw = SESSIONS.get(player.getUUID());
        if (raw == null) return;

        // Once targeting mode owns the next right click, do not also open or
        // mutate the selected machine through its normal block interaction.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        if (expiredOrWrongDimension(player, raw)) {
            terminate(player, raw, "会话已超时或维度已改变");
            return;
        }
        if (player.getEyePosition().distanceToSqr(event.getHitVec().getLocation())
                > MAX_PLAYER_SELECT_DISTANCE_SQR + 1.0E-6) {
            terminate(player, raw, "目标超过频率变送器的 4 格交互距离");
            return;
        }
        selectBlockInternal(player, raw.nonce, event.getPos(), true);
    }

    /** Proactively disarm the client at the legacy twenty-second deadline. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                SESSIONS.remove(entry.getKey(), session);
            } else if (expiredOrWrongDimension(player, session) || !hasTransmitter(player)) {
                if (SESSIONS.remove(entry.getKey(), session)) {
                    sendClosed(player, session.nonce, "频率变送器会话已超时");
                }
            }
        }
    }

    /** Public deterministic seam used by dedicated-server GameTests. */
    public static SelectionResult selectBlock(ServerPlayer player, UUID nonce, BlockPos clickedPos) {
        return selectBlockInternal(player, nonce, clickedPos, false);
    }

    private static SelectionResult selectBlockInternal(ServerPlayer player, UUID nonce,
                                                        BlockPos clickedPos,
                                                        boolean interactionDistanceValidated) {
        if (player == null || nonce == null || clickedPos == null) return SelectionResult.NO_SESSION;
        Session session = active(player, nonce, true);
        if (session == null) return SelectionResult.NO_SESSION;
        if (session.stage == Stage.AUTHORIZE_SOURCE) {
            sendState(player, session, FreqTransmitterStatePacket.PASSWORD_REQUIRED,
                    sourceLabel(player.serverLevel(), session), "请先输入源设备密码");
            return SelectionResult.PASSWORD_REQUIRED;
        }
        if (!canPlayerSelect(player, clickedPos, interactionDistanceValidated)) {
            terminate(player, session, "目标太远、未加载或无权操作");
            return SelectionResult.INVALID_TARGET;
        }

        Resolved resolved = resolveCanonical(player.serverLevel(), clickedPos);
        if (resolved == null || !canPlayerOperateCanonical(player, resolved.pos)) {
            terminate(player, session, "未找到可用的无线设备");
            return SelectionResult.INVALID_TARGET;
        }
        return session.stage == Stage.SELECT_SOURCE
                ? selectSource(player, session, resolved)
                : selectTarget(player, session, resolved);
    }

    private static SelectionResult selectSource(ServerPlayer player, Session session, Resolved resolved) {
        SourceKind kind;
        String expectedPassword;
        if (resolved.blockEntity instanceof MatrixBlockEntity matrix) {
            if (!WirelessSystem.reconcileMatrixNetwork(player.serverLevel(), matrix).active()) {
                terminate(player, session, "矩阵尚未初始化或网络不可用");
                return SelectionResult.INVALID_TARGET;
            }
            kind = SourceKind.MATRIX;
            expectedPassword = matrix.getPassword();
        } else if (resolved.blockEntity instanceof IWirelessNode node) {
            kind = SourceKind.NODE;
            expectedPassword = node.getPassword();
        } else {
            terminate(player, session, "源设备必须是无线矩阵或无线节点");
            return SelectionResult.INVALID_TARGET;
        }

        long now = player.serverLevel().getGameTime();
        // Legacy always presents the password page, including an empty
        // password that the user confirms with Enter.
        Session selected = session.transition(Stage.AUTHORIZE_SOURCE, kind, resolved.pos,
                resolved.blockEntity, null, now);
        SESSIONS.put(player.getUUID(), selected);
        sendState(player, selected, FreqTransmitterStatePacket.PASSWORD_REQUIRED,
                sourceLabel(player.serverLevel(), selected), expectedPassword.isEmpty()
                        ? "该设备没有密码，按 Enter 确认" : "请输入源设备密码");
        return SelectionResult.PASSWORD_REQUIRED;
    }

    private static SelectionResult selectTarget(ServerPlayer player, Session session, Resolved target) {
        Resolved source = resolveCanonical(player.serverLevel(), session.source);
        if (!validStoredSource(player.serverLevel(), session, source)
                || !credentialStillAuthorized(session, source)) {
            terminate(player, session, "源设备已被移除或失效");
            return SelectionResult.CLOSED;
        }

        boolean linked = false;
        if (session.kind == SourceKind.MATRIX && target.blockEntity instanceof IWirelessNode node) {
            MatrixBlockEntity matrix = (MatrixBlockEntity) source.blockEntity;
            if (target.blockEntity instanceof com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity owned
                    && !owned.canManage(player)) {
                terminate(player, session, "只能把自己拥有的节点加入矩阵网络");
                return SelectionResult.INVALID_TARGET;
            }
            if (WirelessSystem.reconcileMatrixNetwork(player.serverLevel(), matrix).active()) {
                // The user authenticated this exact matrix earlier in the same
                // nonce-bound session.  Use the server-owned password here;
                // never echo or persist the submitted plaintext.
                linked = WirelessSystem.linkNode(player.serverLevel(), matrix, node, matrix.getPassword());
            }
        } else if (session.kind == SourceKind.NODE) {
            IWirelessNode node = (IWirelessNode) source.blockEntity;
            boolean protectedNode = node.getPassword() != null && !node.getPassword().isEmpty();
            String serverCredential = protectedNode ? node.getPassword() : "";
            if (target.blockEntity instanceof IWirelessGenerator generator) {
                linked = WirelessSystem.linkGenerator(player.serverLevel(), node, generator,
                        protectedNode, serverCredential);
            } else if (target.blockEntity instanceof IWirelessReceiver receiver) {
                linked = WirelessSystem.linkReceiver(player.serverLevel(), node, receiver,
                        protectedNode, serverCredential);
            }
        }

        if (!linked) {
            terminate(player, session, "连接失败：请检查类型、容量和无线距离");
            return SelectionResult.LINK_FAILED;
        }
        long now = player.serverLevel().getGameTime();
        Session refreshed = session.refresh(now);
        SESSIONS.put(player.getUUID(), refreshed);
        sendState(player, refreshed, FreqTransmitterStatePacket.SELECT_TARGET,
                sourceLabel(player.serverLevel(), refreshed), "连接成功；可继续右击其他目标");
        return SelectionResult.LINKED;
    }

    private static Session active(ServerPlayer player, UUID nonce, boolean notifyExpired) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.nonce.equals(nonce)) return null;
        if (expiredOrWrongDimension(player, session) || !hasTransmitter(player)) {
            if (SESSIONS.remove(player.getUUID(), session) && notifyExpired) {
                sendClosed(player, session.nonce, "会话已失效，请重新打开频率变送器");
            }
            return null;
        }
        return session;
    }

    private static boolean expiredOrWrongDimension(ServerPlayer player, Session session) {
        return player.serverLevel().getGameTime() > session.expiresAt
                || !player.level().dimension().equals(session.dimension);
    }

    private static boolean hasTransmitter(ServerPlayer player) {
        if (player == null) return false;
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        return data.isTerminalInstalled() && data.hasApp(AppRegistry.FREQ_TRANSMITTER.getAppId());
    }

    private static boolean canPlayerSelect(ServerPlayer player, BlockPos pos,
                                           boolean interactionDistanceValidated) {
        ServerLevel level = player.serverLevel();
        return pos != null && level.isLoaded(pos)
                && (interactionDistanceValidated
                    || eyeToBlockDistanceSqr(player, pos) <= MAX_PLAYER_SELECT_DISTANCE_SQR)
                && level.mayInteract(player, pos);
    }

    /** Distance from the eye ray origin to the nearest point of the block AABB. */
    static double eyeToBlockDistanceSqr(ServerPlayer player, BlockPos pos) {
        var eye = player.getEyePosition();
        double x = Math.max(pos.getX(), Math.min(eye.x, pos.getX() + 1.0));
        double y = Math.max(pos.getY(), Math.min(eye.y, pos.getY() + 1.0));
        double z = Math.max(pos.getZ(), Math.min(eye.z, pos.getZ() + 1.0));
        return eye.distanceToSqr(x, y, z);
    }

    private static boolean canPlayerOperateCanonical(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        return pos != null && level.isLoaded(pos)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_CANONICAL_REVALIDATE_DISTANCE_SQR
                && level.mayInteract(player, pos);
    }

    private static boolean validStoredSource(ServerLevel level, Session session, Resolved source) {
        if (source == null || !source.pos.equals(session.source)
                || source.blockEntity != session.sourceIdentity) return false;
        return session.kind == SourceKind.MATRIX
                ? source.blockEntity instanceof MatrixBlockEntity
                : session.kind == SourceKind.NODE && source.blockEntity instanceof IWirelessNode;
    }

    private static boolean credentialStillAuthorized(Session session, Resolved source) {
        if (session.authFingerprint == null || source == null) return false;
        String current = session.kind == SourceKind.MATRIX
                ? ((MatrixBlockEntity) source.blockEntity).getPassword()
                : ((IWirelessNode) source.blockEntity).getPassword();
        return MessageDigest.isEqual(
                session.authFingerprint.getBytes(StandardCharsets.US_ASCII),
                credentialFingerprint(current).getBytes(StandardCharsets.US_ASCII));
    }

    private static String credentialFingerprint(String credential) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            (credential == null ? "" : credential).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Resolves the multiblock proxies that can represent logical wireless machines. */
    private static Resolved resolveCanonical(ServerLevel level, BlockPos clicked) {
        if (level == null || clicked == null || !level.isLoaded(clicked)) return null;
        BlockEntity direct = level.getBlockEntity(clicked);
        if (direct instanceof IWirelessMatrix || direct instanceof IWirelessNode
                || direct instanceof IWirelessGenerator || direct instanceof IWirelessReceiver) {
            return new Resolved(clicked.immutable(), direct);
        }

        if (direct instanceof IDevSubStructure sub) {
            BlockPos mainPos = sub.getMainPos();
            if (mainPos != null && level.isLoaded(mainPos)) {
                BlockEntity main = level.getBlockEntity(mainPos);
                if (main instanceof IDevStructure mainStructure
                        && sub.getStructureId() != null
                        && sub.getStructureId().equals(mainStructure.getStructureId())
                        && (main instanceof IWirelessGenerator || main instanceof IWirelessReceiver)) {
                    return new Resolved(mainPos.immutable(), main);
                }
            }
        }

        BlockState state = level.getBlockState(clicked);
        if (state.getBlock() instanceof MatrixSubBlock) {
            for (BlockPos candidate : BlockPos.betweenClosed(
                    clicked.offset(-1, -1, -1), clicked.offset(1, 0, 1))) {
                if (!level.isLoaded(candidate)) continue;
                BlockState candidateState = level.getBlockState(candidate);
                if (candidateState.getBlock() instanceof Matrix
                        && Matrix.structurePositions(candidate, candidateState).contains(clicked)
                        && level.getBlockEntity(candidate) instanceof MatrixBlockEntity matrix) {
                    return new Resolved(candidate.immutable(), matrix);
                }
            }
        }
        if (state.getBlock() instanceof WindGenBaseSubBlock) {
            BlockPos mainPos = clicked.below();
            if (level.getBlockState(mainPos).getBlock() instanceof WindGenBase
                    && level.getBlockEntity(mainPos) instanceof IWirelessGenerator generator) {
                return new Resolved(mainPos.immutable(), (BlockEntity) generator);
            }
        }
        return null;
    }

    private static boolean passwordMatches(String expected, String supplied) {
        if (expected == null || supplied == null
                || expected.length() > NetworkInputLimits.PASSWORD
                || supplied.length() > NetworkInputLimits.PASSWORD) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static String sourceLabel(ServerLevel level, Session session) {
        Resolved source = session.source == null ? null : resolveCanonical(level, session.source);
        if (source == null) return "";
        if (source.blockEntity instanceof MatrixBlockEntity matrix) {
            return matrix.getSSID().isBlank() ? "无线矩阵" : "无线矩阵: " + matrix.getSSID();
        }
        if (source.blockEntity instanceof IWirelessNode node) {
            return node.getNodeName().isBlank() ? "无线节点" : "无线节点: " + node.getNodeName();
        }
        return "";
    }

    private static String targetPrompt(SourceKind kind) {
        return kind == SourceKind.MATRIX ? "右击一个无线节点以连接"
                : "右击一个无线发电机或耗能机器以连接";
    }

    private static void sendTargetingState(ServerPlayer player, Session session, String message) {
        int state = session.stage == Stage.SELECT_TARGET
                ? FreqTransmitterStatePacket.SELECT_TARGET : FreqTransmitterStatePacket.SELECT_SOURCE;
        sendState(player, session, state, sourceLabel(player.serverLevel(), session), message);
    }

    private static void sendState(ServerPlayer player, Session session, int state,
                                  String sourceLabel, String message) {
        SafePayloadSender.send(player, new FreqTransmitterStatePacket(session.nonce, state,
                session.kind.ordinal(), sourceLabel, message));
    }

    private static void terminate(ServerPlayer player, Session session, String message) {
        SESSIONS.remove(player.getUUID(), session);
        sendClosed(player, session.nonce, message);
    }

    private static void closeWithMessage(ServerPlayer player, UUID nonce, String message) {
        sendClosed(player, nonce == null ? new UUID(0, 0) : nonce, message);
    }

    private static void sendClosed(ServerPlayer player, UUID nonce, String message) {
        SafePayloadSender.send(player, new FreqTransmitterStatePacket(nonce,
                FreqTransmitterStatePacket.CLOSED, SourceKind.NONE.ordinal(), "", message));
    }

    public static void clearPlayer(UUID player) {
        if (player != null) SESSIONS.remove(player);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    static boolean hasSession(UUID player) {
        return player != null && SESSIONS.containsKey(player);
    }
}
