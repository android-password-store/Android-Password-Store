package com.zeapo.pwdstore.autofill;

import android.service.autofill.Dataset;

import java.util.List;

public interface OnAllDatasetsCreatedListener {
    void onAllDatasetsCreated(List<Dataset> datasetList);
}
