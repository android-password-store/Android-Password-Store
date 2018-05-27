package com.zeapo.pwdstore.autofill;

import android.annotation.TargetApi;
import android.content.Intent;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;

import java.io.File;
import java.util.List;

@TargetApi(26)
public class DatasetCreationListener {
    void onDatasetCreated(Dataset dataset) {};
    void onDatasetBatchCreated(List<Dataset> datasetList) {};
    void onAuthenticationRequired(FillResponse authResponse){};
}
