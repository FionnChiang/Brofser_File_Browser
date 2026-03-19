package com.example.filebrowser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    public static final int VIEW_LIST          = 0;
    public static final int VIEW_GRID          = 1;
    public static final int VIEW_GALLERY       = 2;
    public static final int VIEW_LIST_NOICON   = 3;
    /** Search-result mode: list view with an extra path line below info. */
    public static final int VIEW_SEARCH_RESULT = 4;

    /** Internal view type for the loading spinner row. */
    private static final int VIEW_TYPE_LOADING = 100;

    public interface OnFileClickListener {
        void onFileClick(FileItem fileItem);
    }

    public interface OnMultiSelectListener {
        void onEnterMultiSelect();
        void onSelectionChanged(int selectedCount, int totalCount);
        void onExitMultiSelect();
    }

    private final Context context;
    private final OnFileClickListener clickListener;
    private OnMultiSelectListener multiSelectListener;

    private List<FileItem> files = new ArrayList<>();
    private boolean multiSelectMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();

    private int viewMode = VIEW_LIST;

    public FileAdapter(Context context, OnFileClickListener listener) {
        this.context = context;
        this.clickListener = listener;
    }

    public void setMultiSelectListener(OnMultiSelectListener listener) {
        this.multiSelectListener = listener;
    }

    public void setViewMode(int mode) {
        this.viewMode = mode;
        notifyDataSetChanged();
    }

    public int getViewMode() {
        return viewMode;
    }

    public void setFiles(List<FileItem> files) {
        this.files = files;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    /**
     * Inserts a new search result just before the trailing loading-spinner row
     * (if present), or appends it at the end.
     */
    public void addSearchResult(FileItem item) {
        int insertPos = files.size();
        if (!files.isEmpty() && files.get(files.size() - 1).getType() == FileItem.TYPE_LOADING) {
            insertPos = files.size() - 1;
        }
        files.add(insertPos, item);
        notifyItemInserted(insertPos);
    }

    /** Removes the trailing loading-spinner row if present. */
    public void removeLoadingItem() {
        for (int i = files.size() - 1; i >= 0; i--) {
            if (files.get(i).getType() == FileItem.TYPE_LOADING) {
                files.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public boolean isParentDir(int position) {
        return position >= 0 && position < files.size()
                && files.get(position).getName().equals("..");
    }

    public void enterMultiSelectMode(int position) {
        multiSelectMode = true;
        selectedPositions.clear();
        selectedPositions.add(position);
        notifyDataSetChanged();
        if (multiSelectListener != null) {
            multiSelectListener.onEnterMultiSelect();
            multiSelectListener.onSelectionChanged(selectedPositions.size(), files.size());
        }
    }

    public void enterMultiSelectModeEmpty() {
        multiSelectMode = true;
        selectedPositions.clear();
        notifyDataSetChanged();
        if (multiSelectListener != null) {
            multiSelectListener.onEnterMultiSelect();
            multiSelectListener.onSelectionChanged(0, files.size());
        }
    }

    public void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedPositions.clear();
        notifyDataSetChanged();
        if (multiSelectListener != null) {
            multiSelectListener.onExitMultiSelect();
        }
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(selectedPositions.size(), files.size());
        }
    }

    public void selectAll() {
        for (int i = 0; i < files.size(); i++) {
            FileItem item = files.get(i);
            if (!item.getName().equals("..") && item.getType() != FileItem.TYPE_LOADING) {
                selectedPositions.add(i);
            }
        }
        notifyDataSetChanged();
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(selectedPositions.size(), files.size());
        }
    }

    public void deselectAll() {
        selectedPositions.clear();
        notifyDataSetChanged();
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(0, files.size());
        }
    }

    public int getSelectableCount() {
        int count = 0;
        for (FileItem item : files) {
            if (!item.getName().equals("..") && item.getType() != FileItem.TYPE_LOADING) count++;
        }
        return count;
    }

    public List<FileItem> getSelectedItems() {
        List<FileItem> selected = new ArrayList<>();
        for (int pos : selectedPositions) {
            if (pos >= 0 && pos < files.size()) {
                selected.add(files.get(pos));
            }
        }
        return selected;
    }

    // ── RecyclerView.Adapter overrides ────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        if (files.get(position).getType() == FileItem.TYPE_LOADING) return VIEW_TYPE_LOADING;
        return viewMode;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        switch (viewType) {
            case VIEW_TYPE_LOADING:    layoutId = R.layout.item_search_loading; break;
            case VIEW_GRID:            layoutId = R.layout.item_file_grid;      break;
            case VIEW_GALLERY:         layoutId = R.layout.item_file_gallery;   break;
            case VIEW_LIST_NOICON:     layoutId = R.layout.item_file_noicon;    break;
            default:                   layoutId = R.layout.item_file;           break;
        }
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new FileViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem item = files.get(position);
        if (item.getType() == FileItem.TYPE_LOADING) return; // nothing to bind
        boolean selected = selectedPositions.contains(position);
        holder.bind(item, multiSelectMode, selected);
    }

    @Override
    public void onViewRecycled(@NonNull FileViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.ivThumb != null) {
            holder.ivThumb.setTag(null);
            holder.ivThumb.setImageBitmap(null);
        }
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final int type;
        final CheckBox  checkbox;
        final ImageView ivIcon;
        final ImageView ivThumb;
        final TextView  tvName;
        final TextView  tvInfo;
        final TextView  tvPath;   // search-result path line (may be null)

        FileViewHolder(@NonNull View itemView, int type) {
            super(itemView);
            this.type = type;
            checkbox = itemView.findViewById(R.id.checkbox);
            ivIcon   = itemView.findViewById(R.id.ivIcon);
            ivThumb  = itemView.findViewById(R.id.ivThumb);
            tvName   = itemView.findViewById(R.id.tvName);
            tvInfo   = itemView.findViewById(R.id.tvInfo);
            tvPath   = itemView.findViewById(R.id.tvPath);

            // Loading rows are not interactive
            if (type == VIEW_TYPE_LOADING) return;

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                FileItem item = files.get(pos);
                if (item.getType() == FileItem.TYPE_LOADING) return;
                if (multiSelectMode) {
                    if (!item.getName().equals("..")) toggleSelection(pos);
                } else {
                    clickListener.onFileClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                FileItem item = files.get(pos);
                if (item.getType() == FileItem.TYPE_LOADING) return false;
                if (!multiSelectMode && !item.getName().equals("..")) {
                    enterMultiSelectMode(pos);
                    return true;
                }
                return false;
            });
        }

        void bind(FileItem item, boolean multiSelect, boolean selected) {
            if (tvName != null) tvName.setText(item.getName());
            if (tvInfo != null) tvInfo.setText(item.getInfo());

            // Show path in search-result mode
            if (tvPath != null) {
                String path = item.getDisplayPath();
                if (path != null && !path.isEmpty()) {
                    tvPath.setText(path);
                    tvPath.setVisibility(View.VISIBLE);
                } else {
                    tvPath.setVisibility(View.GONE);
                }
            }

            boolean isParentDir = item.getName().equals("..");
            if (checkbox != null) {
                checkbox.setVisibility(multiSelect && !isParentDir ? View.VISIBLE : View.GONE);
                checkbox.setChecked(selected);
            }

            if (selected) {
                itemView.setBackgroundColor(0x1A1565C0);
            } else {
                TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                if (outValue.resourceId != 0) {
                    itemView.setBackgroundResource(outValue.resourceId);
                } else {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            }

            if (type == VIEW_GALLERY) {
                bindGallery(item);
            } else if (ivIcon != null) {
                bindIcon(item);
            }
        }

        private void bindIcon(FileItem item) {
            switch (item.getType()) {
                case FileItem.TYPE_FOLDER:
                    ivIcon.setImageResource(R.drawable.ic_folder);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_folder, null));
                    break;
                case FileItem.TYPE_IMAGE:
                    ivIcon.setImageResource(R.drawable.ic_image);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_image, null));
                    break;
                case FileItem.TYPE_VIDEO:
                    ivIcon.setImageResource(R.drawable.ic_video);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_video, null));
                    break;
                case FileItem.TYPE_AUDIO:
                    ivIcon.setImageResource(R.drawable.ic_audio);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_audio, null));
                    break;
                case FileItem.TYPE_TEXT:
                    ivIcon.setImageResource(R.drawable.ic_text);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_text, null));
                    break;
                default:
                    ivIcon.setImageResource(R.drawable.ic_file);
                    ivIcon.setColorFilter(context.getResources().getColor(R.color.color_file, null));
                    break;
            }
        }

        private void bindGallery(FileItem item) {
            if (item.getType() == FileItem.TYPE_IMAGE && ivThumb != null) {
                ivThumb.setVisibility(View.VISIBLE);
                if (ivIcon != null) ivIcon.setVisibility(View.GONE);

                String path = item.getFile().getAbsolutePath();
                ivThumb.setTag(path);
                ivThumb.setImageBitmap(null);

                new Thread(() -> {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    Bitmap bmp = BitmapFactory.decodeFile(path, opts);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (path.equals(ivThumb.getTag())) {
                            ivThumb.setImageBitmap(bmp);
                        }
                    });
                }).start();
            } else {
                if (ivThumb != null) ivThumb.setVisibility(View.GONE);
                if (ivIcon != null) {
                    ivIcon.setVisibility(View.VISIBLE);
                    bindIcon(item);
                }
            }
        }
    }
}
