/*
 * *****************************************************************
 *
 *  Copyright 2018 DEKRA Testing and Certification, S.A.U. All Rights Reserved.
 *
 *  ******************************************************************
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  ******************************************************************
 */

package org.openconnectivity.otgc.client.presentation.view;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.iotivity.base.OcException;
import org.iotivity.base.OcRepresentation;
import org.openconnectivity.otgc.R;
import org.openconnectivity.otgc.client.domain.model.SerializableResource;
import org.openconnectivity.otgc.client.presentation.viewmodel.ResourceViewModel;
import org.openconnectivity.otgc.common.constant.OcfInterface;
import org.openconnectivity.otgc.common.presentation.viewmodel.ViewModelError;
import org.openconnectivity.otgc.di.Injectable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ResourceFragment extends Fragment implements Injectable {

    private static final int SEEK_BAR_SIZE = 500;
    @BindView(R.id.expand_button) ImageButton mExpandButton;
    @BindView(R.id.device_resource_name) TextView mResourceName;
    @BindView(R.id.device_resource_observation) Switch mResourceObservation;
    @BindView(R.id.introspected_layout) GridLayout mLayout;

    @Inject ViewModelProvider.Factory mViewModelFactory;

    private ResourceViewModel mViewModel;

    private Map<String, View> mViews;

    private String mDeviceId;
    private SerializableResource mResource;
    private boolean mExpanded = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mDeviceId = args.getString("deviceId");
        mResource = (SerializableResource) args.getSerializable("resource");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_resource, container, false);
        ButterKnife.bind(this, view);

        initViews();

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initViewModel();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mResource.isObservable()) {
            mViewModel.cancelObserveRequest(mResource.getUri());
        }
    }

    private void initViews() {
        mViews = new HashMap<>();

        mExpandButton.setOnClickListener(v -> {
            mExpanded = !mExpanded;

            if (getContext() != null) {
                mExpandButton.setImageDrawable(
                        ContextCompat.getDrawable(getContext(),
                                mExpanded ? R.drawable.ic_expand_less_black_36dp : R.drawable.ic_expand_more_black_36dp)
                );
            }
            mResourceObservation.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
            mLayout.setVisibility(mExpanded ? View.VISIBLE : View.GONE);

            if (mExpanded && mViews.isEmpty()) {
                mViewModel.getRequest(mDeviceId, mResource.getUri(), mResource.getResourceTypes(), mResource.getResourceInterfaces());
            }
        });

        if (mResource.isObservable()) {
            mResourceObservation.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    mViewModel.observeRequest(mDeviceId, mResource.getUri(), mResource.getResourceTypes(), mResource.getResourceInterfaces());
                } else {
                    mViewModel.cancelObserveRequest(mResource.getUri());
                }
            });
        } else {
            mResourceObservation.setVisibility(View.GONE);
        }

        mResourceName.setText(mResource.getUri());
    }

    private void initViewModel() {
        mViewModel = ViewModelProviders.of(this, mViewModelFactory).get(ResourceViewModel.class);
        mViewModel.isProcessing().observe(this, this::handleProcessing);
        mViewModel.getError().observe(this, this::handleError);

        mViewModel.getResponse().observe(this, this::processResponse);
    }

    private void handleProcessing(@NonNull Boolean isProcessing) {
        // TODO:
    }

    private void handleError(@NonNull ViewModelError error) {
        int errorId = 0;
        switch ((ResourceViewModel.Error) error.getType()) {
            case GET:
                errorId = R.string.client_fragment_get_request_failed;
                break;
            case POST:
                errorId = R.string.client_fragment_post_request_failed;
                for (View v : mViews.values()) {
                    v.setEnabled(false);
                }
                break;
            case PUT:
                errorId = R.string.client_fragment_put_request_failed;
                break;
            case OBSERVE:
                break;
            case CANCEL_OBSERVE:
                break;
        }

        Toast.makeText(getContext(), errorId, Toast.LENGTH_SHORT).show();
    }

    private void processResponse(@NonNull OcRepresentation response) {
        Map<String, Object> values = response.getValues();

        if (mViews.isEmpty()) {
            mLayout.setRowCount(values.size());
        }

        for (Map.Entry<String, Object> entry : values.entrySet())
            if (mViews.containsKey(entry.getKey())) {
                if (entry.getValue() instanceof Boolean) {
                    ((Switch) mViews.get(entry.getKey())).setChecked((Boolean) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    NumberFormat numberFormat = NumberFormat.getInstance();
                    if (isViewEnabled(response.getResourceInterfaces())) {
                        ((SeekBar) mViews.get(entry.getKey())).setProgress(((Integer) entry.getValue()).intValue());
                        //((EditText) mViews.get(entry.getKey())).setText(numberFormat.format(entry.getValue()));
                    } else {
                        ((TextView) mViews.get(entry.getKey())).setText(numberFormat.format(entry.getValue()));
                    }
                } else if (entry.getValue() instanceof Double) {
                    NumberFormat numberFormat = new DecimalFormat("0.0");
                    if (isViewEnabled(response.getResourceInterfaces())) {
                        ((EditText) mViews.get(entry.getKey())).setText(numberFormat.format(entry.getValue()));
                    } else {
                        ((TextView) mViews.get(entry.getKey())).setText(numberFormat.format(entry.getValue()));
                    }
                }
            } else {
                View view = null;
                final View[] seekview = {null};
                if (entry.getValue() instanceof Boolean) {
                    Switch s = new Switch(getContext());
                    s.setPadding(0,0,10,0);
                    //s.setText(entry.getKey());
                    s.setChecked((Boolean) entry.getValue());
                    if (isViewEnabled(response.getResourceInterfaces())) {
                        s.setOnCheckedChangeListener((v, isChecked) -> {
                            OcRepresentation rep = new OcRepresentation();
                            try {
                                rep.setValue(entry.getKey(), isChecked);
                            } catch (OcException e) {

                            }
                            mViewModel.postRequest(mDeviceId, mResource.getUri(), mResource.getResourceTypes(), mResource.getResourceInterfaces(),
                                    rep);
                        });
                    } else {
                        s.setEnabled(false);
                    }

                    view = s;
                } else if (entry.getValue() instanceof Integer) {
                    if (isViewEnabled(response.getResourceInterfaces())) {
                        SeekBar sb = new SeekBar(getContext());
                        sb.setMax(100);
                        sb.setProgress(((Integer) entry.getValue()).intValue());

                        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                            }

                            @Override
                            public void onStartTrackingTouch(SeekBar seekBar) {

                            }

                            @Override
                            public void onStopTrackingTouch(SeekBar seekBar) {
                                Integer number;

                                try {
                                    number = seekBar.getProgress();
                                } catch (NumberFormatException e) {
                                    return;
                                }

                                OcRepresentation rep = new OcRepresentation();
                                try {
                                    rep.setValue(entry.getKey(), number);
                                } catch (OcException e) {

                                }
                                mViewModel.postRequest(mDeviceId, mResource.getUri(), mResource.getResourceTypes(), mResource.getResourceInterfaces(),
                                        rep);
                            }
                        });
                        /*EditText et = new EditText(getContext());
                        et.setTextColor(Color.WHITE);
                        et.setInputType(InputType.TYPE_CLASS_NUMBER);
                        NumberFormat numberFormat = NumberFormat.getInstance();
                        et.setText(numberFormat.format(entry.getValue()));
                        et.addTextChangedListener(new TextWatcher() {

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                Integer number;

                                try {
                                    number = Integer.valueOf(s.toString());
                                } catch (NumberFormatException e) {
                                    return;
                                }

                                OcRepresentation rep = new OcRepresentation();
                                try {
                                    rep.setValue(entry.getKey(), number);
                                } catch (OcException e) {

                                }
                                mViewModel.postRequest(mDeviceId, mResource.getUri(), mResource.getResourceTypes(), mResource.getResourceInterfaces(),
                                        rep);
                            }
                        });*/

                        view= sb;
                    } else {
                        TextView tv = new TextView(getContext());
                        NumberFormat numberFormat = NumberFormat.getInstance();
                        tv.setTextColor(Color.WHITE);
                        tv.setText(numberFormat.format(entry.getValue()));

                        view = tv;
                    }
                } else if (entry.getValue() instanceof Double) {
                    if (isViewEnabled(response.getResourceInterfaces())) {
                        EditText et = new EditText(getContext());
                        et.setTextColor(Color.WHITE);
                        et.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        NumberFormat numberFormat = new DecimalFormat("0.0");
                        et.setText(numberFormat.format(entry.getValue()));

                        view = et;
                    } else {
                        TextView tv = new TextView(getContext());
                        NumberFormat numberFormat = new DecimalFormat("0.0");
                        tv.setTextColor(Color.WHITE);
                        tv.setText(numberFormat.format(entry.getValue()));

                        view = tv;
                    }
                }

                if (view != null) {
                    mViews.put(entry.getKey(), view);
                    TextView title = new TextView(getContext());
                    title.setTextColor(Color.WHITE);
                    title.setText(entry.getKey());
                    mLayout.addView(title);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(SEEK_BAR_SIZE,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins((int) (8 * getResources().getDisplayMetrics().density), 0, 0, 0);
                    view.setLayoutParams(params);
                    mLayout.addView(view);
                }
            }
    }

    private boolean isViewEnabled(List<String> resourceInterfaces) {
        return resourceInterfaces.isEmpty()
                || resourceInterfaces.contains(OcfInterface.ACTUATOR)
                || resourceInterfaces.contains(OcfInterface.READ_WRITE);
    }
}
