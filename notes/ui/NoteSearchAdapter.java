package net.micode.notes.ui;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HISTORY = 1;
    private static final int TYPE_NOTE = 2;

    private Context mContext;
    private List<Object> mDataList;
    private String mSearchKeyword;
    private OnItemClickListener mListener;

    public interface OnItemClickListener {
        void onNoteClick(NotesRepository.NoteInfo note);
        void onHistoryClick(String keyword);
        void onHistoryDelete(String keyword);
    }

    public NoteSearchAdapter(Context context, OnItemClickListener listener) {
        mContext = context;
        mListener = listener;
        mDataList = new ArrayList<>();
    }

    public void setData(List<Object> data, String keyword) {
        mDataList = data;
        mSearchKeyword = keyword;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = mDataList.get(position);
        if (item instanceof String) {
            return TYPE_HISTORY;
        } else if (item instanceof NotesRepository.NoteInfo) {
            return TYPE_NOTE;
        }
        return super.getItemViewType(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HISTORY) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.search_history_item, parent, false);
            return new HistoryViewHolder(view);
        } else {
            View view = LayoutInflater.from(mContext).inflate(R.layout.note_item, parent, false);
            return new NoteViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HistoryViewHolder) {
            String keyword = (String) mDataList.get(position);
            ((HistoryViewHolder) holder).bind(keyword);
        } else if (holder instanceof NoteViewHolder) {
            NotesRepository.NoteInfo note = (NotesRepository.NoteInfo) mDataList.get(position);
            ((NoteViewHolder) holder).bind(note);
        }
    }

    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvKeyword;
        ImageView ivDelete;

        public HistoryViewHolder(View itemView) {
            super(itemView);
            tvKeyword = itemView.findViewById(R.id.tv_history_keyword);
            ivDelete = itemView.findViewById(R.id.iv_delete_history);
        }

        public void bind(final String keyword) {
            tvKeyword.setText(keyword);
            itemView.setOnClickListener(v -> {
                if (mListener != null) mListener.onHistoryClick(keyword);
            });
            ivDelete.setOnClickListener(v -> {
                if (mListener != null) mListener.onHistoryDelete(keyword);
            });
        }
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        ImageView ivTypeIcon;
        TextView tvTitle;
        TextView tvTime;
        TextView tvName;
        ImageView ivAlertIcon;
        CheckBox checkbox;

        public NoteViewHolder(View itemView) {
            super(itemView);
            ivTypeIcon = itemView.findViewById(R.id.iv_type_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvName = itemView.findViewById(R.id.tv_name);
            ivAlertIcon = itemView.findViewById(R.id.iv_alert_icon);
            checkbox = itemView.findViewById(android.R.id.checkbox);
        }

        public void bind(final NotesRepository.NoteInfo note) {
            // 设置标题和高亮
            // NoteInfo.title defaults to snippet if title is empty, so it's safe to use title
            if (!TextUtils.isEmpty(mSearchKeyword)) {
                tvTitle.setText(getHighlightText(note.title, mSearchKeyword));
            } else {
                tvTitle.setText(note.title);
            }

            // 设置时间
            tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(note.modifiedDate));

            // 设置背景（如果 NoteInfo 中有背景ID）
            // 注意：NoteInfo 中 bgColorId 是整型ID，需要转换为资源ID
            // 这里为了简单，暂不设置复杂的背景，或者使用默认背景

            // 点击事件
            itemView.setOnClickListener(v -> {
                if (mListener != null) mListener.onNoteClick(note);
            });
            
            // 隐藏不需要的视图
            ivTypeIcon.setVisibility(View.GONE);
            tvName.setVisibility(View.GONE);
            checkbox.setVisibility(View.GONE);
            ivAlertIcon.setVisibility(View.GONE);
        }
    }

    private Spannable getHighlightText(String text, String keyword) {
        if (text == null) text = "";
        SpannableString spannable = new SpannableString(text);
        if (!TextUtils.isEmpty(keyword)) {
            Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                spannable.setSpan(
                        new BackgroundColorSpan(0x40FFFF00), // 半透明黄色
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }
        return spannable;
    }
}
