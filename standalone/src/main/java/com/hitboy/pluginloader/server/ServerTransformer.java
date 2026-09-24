package com.hitboy.pluginloader.server;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patches a handful of vanilla server methods as they load, so they call {@link HitBoyServerHooks}.
 * Hooks are declared with Mojang's names and translated through {@link Mappings}; the server JAR on
 * disk is never modified.
 */
final class ServerTransformer implements ClassFileTransformer {
    private static final String HOOKS = "com/hitboy/pluginloader/server/HitBoyServerHooks";

    private enum Kind {
        /** Call the hook at method entry. */
        ENTRY,
        /** Call the hook before every return. */
        BEFORE_RETURN,
        /** At entry, return early (void, or {@code false}) when the hook returns true. */
        CANCEL,
        /** At entry, replace argument 1 with the hook's result, or return early when it is null. */
        REPLACE_FIRST_ARGUMENT
    }

    private static final class Hook {
        final String owner;
        final String name;
        final String descriptor;
        final Kind kind;
        final String hookMethod;
        final int[] slots;
        final boolean required;
        String runtimeOwner;
        String runtimeName;
        String runtimeDescriptor;
        boolean applied;

        Hook(String owner, String name, String descriptor, Kind kind, String hookMethod, boolean required, int... slots) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.kind = kind;
            this.hookMethod = hookMethod;
            this.slots = slots;
            this.required = required;
        }
    }

    private final List<Hook> hooks = new ArrayList<>();
    private final Map<String, List<Hook>> hooksByClass = new HashMap<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    ServerTransformer(Mappings mappings) {
        String player = "Lnet/minecraft/server/level/ServerPlayer;";
        // Return-site hooks take no locals: obfuscated builds may drop "this" from the frame before returning.
        hooks.add(new Hook("net.minecraft.server.dedicated.DedicatedServer", "initServer", "()Z", Kind.ENTRY, "serverStarting", true, 0));
        hooks.add(new Hook("net.minecraft.server.dedicated.DedicatedServer", "initServer", "()Z", Kind.BEFORE_RETURN, "serverStarted", true));
        hooks.add(new Hook("net.minecraft.server.MinecraftServer", "stopServer", "()V", Kind.ENTRY, "serverStopping", true, 0));
        hooks.add(new Hook("net.minecraft.commands.Commands", "<init>",
            "(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V",
            Kind.BEFORE_RETURN, "commandsCreated", true, 0));
        hooks.add(new Hook("net.minecraft.server.players.PlayerList", "placeNewPlayer",
            "(Lnet/minecraft/network/Connection;" + player + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            Kind.ENTRY, "beginJoin", true, 2));
        hooks.add(new Hook("net.minecraft.server.players.PlayerList", "placeNewPlayer",
            "(Lnet/minecraft/network/Connection;" + player + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            Kind.BEFORE_RETURN, "endJoin", true));
        hooks.add(new Hook("net.minecraft.server.players.PlayerList", "broadcastSystemMessage",
            "(Lnet/minecraft/network/chat/Component;Z)V", Kind.REPLACE_FIRST_ARGUMENT, "systemMessage", true, 0, 1));
        hooks.add(new Hook("net.minecraft.server.network.ServerGamePacketListenerImpl", "removePlayerFromWorld", "()V",
            Kind.ENTRY, "beginQuit", true, 0));
        hooks.add(new Hook("net.minecraft.server.network.ServerGamePacketListenerImpl", "removePlayerFromWorld", "()V",
            Kind.BEFORE_RETURN, "endQuit", true));
        hooks.add(new Hook("net.minecraft.server.network.ServerGamePacketListenerImpl", "broadcastChatMessage",
            "(Lnet/minecraft/network/chat/PlayerChatMessage;)V", Kind.CANCEL, "chat", true, 0, 1));
        hooks.add(new Hook("net.minecraft.server.level.ServerPlayerGameMode", "destroyBlock",
            "(Lnet/minecraft/core/BlockPos;)Z", Kind.CANCEL, "blockBreak", true, 0, 1));

        for (Hook hook : hooks) {
            hook.runtimeOwner = mappings.internalName(hook.owner);
            hook.runtimeDescriptor = mappings.descriptor(hook.descriptor);
            hook.runtimeName = "<init>".equals(hook.name) ? hook.name
                : mappings.methodName(hook.owner, hook.name, parameterList(hook.descriptor));
            hooksByClass.computeIfAbsent(hook.runtimeOwner, key -> new ArrayList<>()).add(hook);
        }
    }

    /** Hooks whose target class has loaded but did not contain the expected method. */
    List<String> missingHooks() {
        return new ArrayList<>(failed);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        List<Hook> classHooks = className == null ? null : hooksByClass.get(className);
        if (classHooks == null) return null;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            for (Hook hook : classHooks) {
                for (MethodNode method : node.methods) {
                    if (method.name.equals(hook.runtimeName) && method.desc.equals(hook.runtimeDescriptor)) {
                        inject(method, hook);
                        hook.applied = true;
                    }
                }
            }
            for (Hook hook : classHooks) {
                if (hook.required && !hook.applied && failed.add(hook.owner + "#" + hook.name)) {
                    HitBoyServerHooks.log("HitBoy hook not found in this Minecraft version: " + hook.owner + "#" + hook.name);
                }
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable failure) {
            failed.add(className + ": " + failure);
            HitBoyServerHooks.log("Could not patch " + className + ": " + failure);
            return null;
        }
    }

    private void inject(MethodNode method, Hook hook) {
        Type returnType = Type.getReturnType(method.desc);
        switch (hook.kind) {
            case ENTRY:
                method.instructions.insert(call(hook, method, "V"));
                break;
            case BEFORE_RETURN:
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    int opcode = instruction.getOpcode();
                    if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                        method.instructions.insertBefore(instruction, call(hook, method, "V"));
                    }
                }
                break;
            case CANCEL: {
                InsnList list = call(hook, method, "Z");
                LabelNode proceed = new LabelNode();
                list.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                if (returnType.getSort() == Type.BOOLEAN) list.add(new InsnNode(Opcodes.ICONST_0));
                list.add(new InsnNode(returnType.getSort() == Type.VOID ? Opcodes.RETURN : Opcodes.IRETURN));
                list.add(proceed);
                list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(list);
                break;
            }
            case REPLACE_FIRST_ARGUMENT: {
                InsnList list = call(hook, method, "Ljava/lang/Object;");
                LabelNode keep = new LabelNode();
                list.add(new InsnNode(Opcodes.DUP));
                list.add(new JumpInsnNode(Opcodes.IFNONNULL, keep));
                list.add(new InsnNode(Opcodes.POP));
                list.add(new InsnNode(Opcodes.RETURN));
                list.add(keep);
                list.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Object"}));
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getArgumentTypes(method.desc)[0].getInternalName()));
                list.add(new VarInsnNode(Opcodes.ASTORE, 1));
                method.instructions.insert(list);
                break;
            }
            default:
                throw new IllegalStateException(hook.kind.name());
        }
    }

    private InsnList call(Hook hook, MethodNode method, String returnDescriptor) {
        InsnList list = new InsnList();
        StringBuilder descriptor = new StringBuilder("(");
        for (int slot : hook.slots) {
            list.add(new VarInsnNode(Opcodes.ALOAD, slot));
            descriptor.append("Ljava/lang/Object;");
        }
        descriptor.append(')').append(returnDescriptor);
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook.hookMethod, descriptor.toString(), false));
        return list;
    }

    private static String parameterList(String descriptor) {
        StringBuilder parameters = new StringBuilder();
        for (Type type : Type.getArgumentTypes(descriptor)) {
            if (parameters.length() > 0) parameters.append(',');
            parameters.append(type.getClassName());
        }
        return parameters.toString();
    }
}
