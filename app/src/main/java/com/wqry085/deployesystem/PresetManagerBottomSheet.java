package com.wqry085.deployesystem;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wqry085.deployesystem.PayloadPresetManager.Preset;

import java.util.List;

/**
 * Payload 预设管理 BottomSheet
 * 支持多预设：新建 / 重命名 / 删除 / 运行 / 设为开机预设
 */
public class PresetManagerBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private PresetAdapter adapter;

    private Runnable onChangedCallback;
    private PayloadProvider payloadProvider;

    public interface PayloadProvider {
        String buildPayload();
    }

    public void setOnChangedCallback(Runnable callback) {
        this.onChangedCallback = callback;
    }

    public void setPayloadProvider(PayloadProvider provider) {
        this.payloadProvider = provider;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_preset_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.preset_recycler);
        emptyView = view.findViewById(R.id.preset_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        view.findViewById(R.id.btn_add_preset).setOnClickListener(v -> showNameDialog(null));

        refreshList();
    }

    private void refreshList() {
        List<Preset> presets = PayloadPresetManager.getPresets(requireContext());
        if (adapter == null) {
            adapter = new PresetAdapter(presets);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.update(presets);
        }
        emptyView.setVisibility(presets.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(presets.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showNameDialog(@Nullable Preset existing) {
        Context ctx = requireContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View dialogView = inflater.inflate(R.layout.dialog_single_input, null);
        com.google.android.material.textfield.TextInputLayout til =
                dialogView.findViewById(R.id.text_input_layout);
        android.widget.EditText editText = til.getEditText();
        til.setHint(existing == null ? "输入预设名称" : "重命名预设");
        if (existing != null && editText != null) {
            editText.setText(existing.name);
            editText.setSelection(existing.name.length());
        }

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? "新建预设" : "重命名预设")
                .setView(dialogView)
                .setPositiveButton(existing == null ? "保存" : "重命名", (dialog, which) -> {
                    if (editText == null) return;
                    String name = editText.getText().toString().trim();
                    if (name.isEmpty()) {
                        til.setError("请输入名称");
                        return;
                    }
                    // 保存当前参数为预设
                    if (existing == null) {
                        String payload = buildCurrentPayload();
                        if (payload == null) {
                            Toast.makeText(ctx, "构建 payload 失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        PayloadPresetManager.addPreset(ctx, name, payload, "自定义参数");
                    } else {
                        String payload = PayloadPresetManager.getPresetById(ctx, existing.id).payload;
                        PayloadPresetManager.updatePreset(ctx, existing.id, name, payload, "自定义参数");
                    }
                    refreshList();
                    if (onChangedCallback != null) onChangedCallback.run();
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String buildCurrentPayload() {
        if (payloadProvider != null) {
            return payloadProvider.buildPayload();
        }
        return null;
    }

    private void showActionsDialog(Preset preset) {
        String bootId = PayloadPresetManager.getBootPresetId(requireContext());
        boolean isBoot = preset.id.equals(bootId);

        String[] actions = isBoot
                ? new String[]{"运行", "重命名", "取消开机预设", "删除"}
                : new String[]{"运行", "设为开机预设", "重命名", "删除"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(preset.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            runPreset(preset);
                            break;
                        case 1:
                            if (isBoot) {
                                PayloadPresetManager.setBootPresetId(requireContext(), null);
                                Toast.makeText(requireContext(), "已取消开机预设", Toast.LENGTH_SHORT).show();
                            } else {
                                PayloadPresetManager.setBootPresetId(requireContext(), preset.id);
                                Toast.makeText(requireContext(), "已设为开机预设", Toast.LENGTH_SHORT).show();
                            }
                            refreshList();
                            if (onChangedCallback != null) onChangedCallback.run();
                            break;
                        case 2:
                            if (isBoot) {
                                deletePreset(preset);
                            } else {
                                showNameDialog(preset);
                            }
                            break;
                        case 3:
                            deletePreset(preset);
                            break;
                    }
                })
                .show();
    }

    private void runPreset(Preset preset) {
        // 🔒 硬性拦截：预设含重启类命令 → 100% 卡开机
        String rebootReason = PayloadGuard.findRebootReason(preset.payload);
        if (rebootReason != null) {
            Toast.makeText(requireContext(), "已阻止: " + rebootReason + "（重启命令 100% 卡开机）", Toast.LENGTH_LONG).show();
            return;
        }
        String result = PayloadPresetManager.injectPresetById(requireContext(), preset.id);
        if (result == null) {
            Toast.makeText(requireContext(), "已注入: " + preset.name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "注入失败: " + result, Toast.LENGTH_LONG).show();
        }
    }

    private void deletePreset(Preset preset) {
        PayloadPresetManager.deletePreset(requireContext(), preset.id);
        refreshList();
        if (onChangedCallback != null) onChangedCallback.run();
    }

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.VH> {
        private List<Preset> list;

        PresetAdapter(List<Preset> list) {
            this.list = list;
        }

        void update(List<Preset> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_preset, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Preset p = list.get(position);
            holder.name.setText(p.name);
            holder.summary.setText(p.summary);

            String bootId = PayloadPresetManager.getBootPresetId(requireContext());
            holder.bootBadge.setVisibility(p.id.equals(bootId) ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> runPreset(p));
            holder.moreBtn.setOnClickListener(v -> showActionsDialog(p));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name;
            TextView summary;
            TextView bootBadge;
            ImageButton moreBtn;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.preset_name);
                summary = itemView.findViewById(R.id.preset_summary);
                bootBadge = itemView.findViewById(R.id.boot_badge);
                moreBtn = itemView.findViewById(R.id.btn_preset_more);
            }
        }
    }
}