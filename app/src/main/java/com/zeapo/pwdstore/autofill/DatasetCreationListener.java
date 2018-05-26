package com.zeapo.pwdstore.autofill;

import android.service.autofill.Dataset;

import java.util.List;

public class DatasetCreationListener {
    void onDatasetCreated(Dataset dataset) {};
    void onDatasetBatchCreated(List<Dataset> datasetList) {};
}
