package net.micode.notes.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.databinding.NoteItemSwipeBinding;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * 笔记信息适配器
 * <p>
 * 支持 RecyclerView，集成滑动菜单、多选模式和回收站模式。
 * 使用 ViewBinding 访问视图。
 * </p>
 */
public class NoteInfoAdapter extends RecyclerView.Adapter<NoteInfoAdapter.ViewHolder> {

    private static final String TAG = "NoteInfoAdapter";
    private Context context;
    private List<NotesRepository.NoteInfo> notes;
    private HashSet<Long> selectedIds;

    // 监听器
    private OnNoteItemClickListener itemClickListener;
    private OnNoteItemLongClickListener itemLongClickListener;
    private OnSwipeMenuClickListener swipeMenuClickListener;

    // 状态
    private boolean isTrashMode = false;
    private boolean isMultiSelectMode = false;

    public interface OnNoteItemClickListener {
        void onNoteItemClick(int position, long noteId);
    }

    public interface OnNoteItemLongClickListener {
        void onNoteItemLongClick(int position, long noteId);
    }

    public interface OnSwipeMenuClickListener {
        void onSwipeEdit(long itemId);
        void onSwipePin(long itemId);
        void onSwipeMove(long itemId);
        void onSwipeDelete(long itemId);
        void onSwipeRename(long itemId);
        void onSwipeRestore(long itemId);
        void onSwipePermanentDelete(long itemId);
    }

    public NoteInfoAdapter(Context context) {
        this.context = context;
        this.notes = new ArrayList<>();
        this.selectedIds = new HashSet<>();
    }

    public void setNotes(List<NotesRepository.NoteInfo> notes) {
        this.notes = notes != null ? notes : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setTrashMode(boolean isTrashMode) {
        this.isTrashMode = isTrashMode;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean isSelectionMode) {
        this.isMultiSelectMode = isSelectionMode;
        notifyDataSetChanged();
    }

    public void setSelectedIds(HashSet<Long> selectedIds) {
        this.selectedIds = selectedIds != null ? new HashSet<>(selectedIds) : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setOnNoteItemClickListener(OnNoteItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnNoteItemLongClickListener(OnNoteItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    public void setOnSwipeMenuClickListener(OnSwipeMenuClickListener listener) {
        this.swipeMenuClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        NoteItemSwipeBinding binding = NoteItemSwipeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotesRepository.NoteInfo note = notes.get(position);
        if (note == null) return;

        NoteItemSwipeBinding binding = holder.binding;
        SwipeMenuLayout swipeLayout = (SwipeMenuLayout) binding.getRoot();

        swipeLayout.setItemId(note.getId());
        swipeLayout.setSwipeEnabled(!isMultiSelectMode);

        // 设置菜单可见性
        if (isTrashMode) {
            binding.swipeMenuNormal.getRoot().setVisibility(View.GONE);
            binding.swipeMenuTrash.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.swipeMenuNormal.getRoot().setVisibility(View.VISIBLE);
            binding.swipeMenuTrash.getRoot().setVisibility(View.GONE);
        }

        // 绑定内容
        if (note.type == Notes.TYPE_FOLDER) {
            String folderName = note.snippet;
            if (TextUtils.isEmpty(folderName)) {
                folderName = "未命名文件夹";
            }
            binding.tvTitle.setText(folderName + " (" + note.notesCount + ")");
            binding.tvTime.setVisibility(View.GONE);
            binding.ivTypeIcon.setVisibility(View.VISIBLE);
            binding.ivTypeIcon.setImageResource(R.drawable.ic_folder);
        } else {
            String title = note.title;
            if (TextUtils.isEmpty(title)) {
                title = "无标题";
            }
            binding.tvTitle.setText(title);
            binding.tvTime.setText(formatDate(note.modifiedDate));
            binding.tvTime.setVisibility(View.VISIBLE);
            binding.ivTypeIcon.setVisibility(View.GONE);
        }

        // 背景颜色和圆角卡片样式
        int color = net.micode.notes.tool.ResourceParser.getNoteBgColor(context, note.bgColorId);
        
        // 使用 CardView 设置背景颜色
        binding.contentCard.setCardBackgroundColor(color);
        binding.contentCard.setActivated(selectedIds.contains(note.getId()));

        // 图标状态
        binding.checkbox.setVisibility(isMultiSelectMode ? View.VISIBLE : View.GONE);
        binding.checkbox.setChecked(selectedIds.contains(note.getId()));
        binding.ivPinnedIcon.setVisibility(note.isPinned ? View.VISIBLE : View.GONE);
        binding.ivLockIcon.setVisibility(note.isLocked ? View.VISIBLE : View.GONE);

        // 滑动菜单按钮点击
        swipeLayout.setOnMenuButtonClickListener(new SwipeMenuLayout.OnMenuButtonClickListener() {
            @Override
            public void onEdit(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipeEdit(itemId); }
            @Override
            public void onPin(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipePin(itemId); }
            @Override
            public void onMove(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipeMove(itemId); }
            @Override
            public void onDelete(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipeDelete(itemId); }
            @Override
            public void onRename(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipeRename(itemId); }
            @Override
            public void onRestore(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipeRestore(itemId); }
            @Override
            public void onPermanentDelete(long itemId) { if (swipeMenuClickListener != null) swipeMenuClickListener.onSwipePermanentDelete(itemId); }
        });

        // 内容区域点击
        swipeLayout.setOnContentClickListener(itemId -> {
            if (itemClickListener != null) {
                itemClickListener.onNoteItemClick(holder.getAdapterPosition(), itemId);
            }
        });

        swipeLayout.setOnContentLongClickListener(itemId -> {
            if (itemLongClickListener != null) {
                itemLongClickListener.onNoteItemLongClick(holder.getAdapterPosition(), itemId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        NoteItemSwipeBinding binding;
        public ViewHolder(NoteItemSwipeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
