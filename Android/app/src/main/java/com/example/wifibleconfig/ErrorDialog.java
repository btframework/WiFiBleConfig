/*
 * Copyright (C) 2023 Marina Petrichenko
 * 
 * marina@btframework.com  
 *   https://www.facebook.com/marina.petrichenko.1  
 *   https://www.btframework.com
 * 
 * It is free for non-commercial and/or education use only.
 *   
 */

package com.example.wifibleconfig;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

public class ErrorDialog {
    public static void showErrorMessage(Context context, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getResources().getString(R.string.error_dlg_title));
        builder.setMessage(context.getResources().getString(messageId));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}
