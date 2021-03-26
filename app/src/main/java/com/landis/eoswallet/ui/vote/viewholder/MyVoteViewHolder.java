package com.landis.eoswallet.ui.vote.viewholder;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.ViewGroup;

import com.landis.eoswallet.R;
import com.landis.eoswallet.base.viewholder.BaseViewHolder;
import com.landis.eoswallet.databinding.ItemMyVoteBinding;
import com.landis.eoswallet.ui.vote.viewmodel.item.MyVoteItemViewModel;

public class MyVoteViewHolder extends BaseViewHolder<ItemMyVoteBinding, MyVoteItemViewModel> {

    public MyVoteViewHolder(@NonNull ViewGroup parent) {
        super(parent, R.layout.item_my_vote);
    }

    @Override
    protected void initViewModel() {
        mViewModel = new MyVoteItemViewModel();
    }

    @Override
    protected void bindViewModel() {
        mDataBinding.setViewModel(mViewModel);
    }

    @Override
    protected void init() {
    }

    public void setContext(Context context){
        mViewModel.setContext(context);
    }
}
