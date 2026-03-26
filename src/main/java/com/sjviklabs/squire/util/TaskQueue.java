package com.sjviklabs.squire.util;

import com.sjviklabs.squire.config.SquireConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FIFO queue of commands for the squire to execute sequentially.
 *
 * Commands: /squire queue add <command>, /squire queue list, /squire queue clear
 * Execution: when current task completes (state → IDLE), pop next task from queue.
 * Persistence: serialized to entity NBT.
 * Queue pauses if squire enters combat (survival priority); resumes after.
 *
 * Each task stores a command name and optional arguments as NBT.
 */
public class TaskQueue {

    private final Deque<SquireTask> tasks = new ArrayDeque<>();

    /** A queued task with a command name and optional arguments. */
    public record SquireTask(String commandName, CompoundTag args) {

        /** Serialize to NBT. */
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("command", commandName);
            tag.put("args", args.copy());
            return tag;
        }

        /** Deserialize from NBT. */
        public static SquireTask load(CompoundTag tag) {
            String cmd = tag.getString("command");
            CompoundTag args = tag.getCompound("args");
            return new SquireTask(cmd, args);
        }
    }

    // ---- Queue operations ----

    /** Add a task to the end of the queue. Returns false if queue is full. */
    public boolean add(SquireTask task) {
        if (tasks.size() >= SquireConfig.maxQueueLength.get()) {
            return false;
        }
        tasks.addLast(task);
        return true;
    }

    /** Add a task by command name with empty args. */
    public boolean add(String commandName) {
        return add(new SquireTask(commandName, new CompoundTag()));
    }

    /** Add a task by command name with args. */
    public boolean add(String commandName, CompoundTag args) {
        return add(new SquireTask(commandName, args));
    }

    /** Pop the next task from the front of the queue. Returns null if empty. */
    @Nullable
    public SquireTask poll() {
        return tasks.pollFirst();
    }

    /** Peek at the next task without removing it. Returns null if empty. */
    @Nullable
    public SquireTask peek() {
        return tasks.peekFirst();
    }

    /** Clear all tasks. */
    public void clear() {
        tasks.clear();
    }

    /** Get the number of queued tasks. */
    public int size() {
        return tasks.size();
    }

    /** Check if the queue is empty. */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    /** Get an immutable snapshot of all tasks for display. */
    public List<SquireTask> getAll() {
        return new ArrayList<>(tasks);
    }

    // ---- NBT persistence ----

    /** Save the queue to an NBT tag. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (SquireTask task : tasks) {
            list.add(task.save());
        }
        tag.put("tasks", list);
        return tag;
    }

    /** Load the queue from an NBT tag. Clears existing tasks. */
    public void load(CompoundTag tag) {
        tasks.clear();
        if (!tag.contains("tasks")) return;

        ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
        int max = SquireConfig.maxQueueLength.get();
        for (int i = 0; i < list.size() && i < max; i++) {
            tasks.addLast(SquireTask.load(list.getCompound(i)));
        }
    }
}