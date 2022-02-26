package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;

public class PermissionManager {
    private HashMap<String, Permission> permissions;
    private int blockingRequestCode = -1;
    private List<String> remainingNames;
    private ArrayList<String> groupedNames;
    private static final int GROUP_PERMISSION_CODE = 0;

    public PermissionManager(Permission... permissions){
        this.permissions = new HashMap<>();
        for (Permission p:permissions) {
            this.permissions.put(p.name, p);
        }
    }

    public void addPermissions(Collection<Permission> permissions){
        for (Permission p:permissions) {
            this.permissions.put(p.name, p);
        }
    }

    /**
     *
     * @param permission the permission to check
     * @param requestCode The request code that will be used to request permission
     * @param explanation An explanation of why the permission is necessary (for the user)
     * @return true if the permission has already been granted
     */
    public static boolean checkPermission(Activity activity, String permission, int requestCode,
                                          @Nullable String explanation){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                permission)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    permission)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                String[] tmp = permission.split("\\.");
                String pName = tmp[tmp.length-1].replaceAll("_", " ").toLowerCase();
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.request_permission_title, pName))
                        .setMessage(explanation==null?"I'm going to request a permission to make " +
                                "the app work better for you":explanation)
                        .setNeutralButton("Ok", (dialogInterface, i) -> {
                            //Prompt the user once explanation has been shown
                            ActivityCompat.requestPermissions(activity,
                                    new String[]{permission},
                                    requestCode);
                        }).show();
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(activity,
                        new String[]{permission},
                        requestCode);
                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
            return true;
        }
        return false;
    }

    public void checkPermissions(Activity activity, ArrayList<String> names, boolean blocking){
        if(blocking) checkPermissionsBlocking(activity, names);
        else{
            ArrayList<String> codes = new ArrayList<>();
            groupedNames = new ArrayList<>();
            for (String name:names){
                Permission p = permissions.getOrDefault(name, null);
                if(p == null) continue;
                if (ContextCompat.checkSelfPermission(activity, p.code)
                        != PackageManager.PERMISSION_GRANTED) {
                    groupedNames.add(p.name);
                    codes.add(p.code);
                } else{
                    if(!p.singleGrant || !p.beenGrantedOnce()){
                        p.grantedOnce = true;
                        p.onGranted.accept(activity, Procedure.empty);
                    }
                }
            }
            // request the permissions
            ActivityCompat.requestPermissions(activity,
                    codes.toArray(new String[0]), GROUP_PERMISSION_CODE);
        }
    }

    private void checkPermissionsBlocking(Activity activity, List<String> names){
        for (String name:names) {
            Permission permission = permissions.get(name);
            if (ContextCompat.checkSelfPermission(activity,
                    permission.code)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                this.blockingRequestCode = permission.requestCode;
                this.remainingNames = names.subList(names.indexOf(name)+1, names.size());

                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        permission.code)) {
                    // Show an explanation to the user After the user
                    // sees the explanation, try again to request the permission.
                    new AlertDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.request_permission_title, permission.name))
                            .setMessage(permission.explanation == null ||
                                    permission.explanation.replaceAll(" ","").length()==0
                                    ? "I'm going to request a permission to make " +
                                    "the app work better for you" : permission.explanation)
                            .setNeutralButton("Ok", (dialogInterface, i) -> {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(activity,
                                        new String[]{permission.code},
                                        permission.requestCode);
                            }).show();
                } else {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(activity,
                            new String[]{permission.code},
                            permission.requestCode);
                }
                return;
            } else {
                if(!permission.singleGrant || !permission.beenGrantedOnce()){
                    permission.grantedOnce = true;
                    permission.onGranted.accept(activity, Procedure.empty);
                }
            }
        }
    }

    public void onRequestPermissionsResult(Activity activity, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        if(GROUP_PERMISSION_CODE == requestCode){
            if(grantResults.length > 0) {
                for (int i = 0; i < groupedNames.size(); i++) {
                    if(grantResults[i] == PackageManager.PERMISSION_GRANTED)
                        this.permissions.get(groupedNames.get(i)).onGranted.accept(activity, Procedure.empty);
                    else this.permissions.get(groupedNames.get(i)).onDenied.accept(activity, Procedure.empty);
                }
            }
        } else {
            for (Permission p:this.permissions.values()) {
                if(p.requestCode == requestCode){
                    if (!(grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                        p.onDenied.accept(activity,
                                ()->checkNextBlockingPermission(activity, requestCode));
                    else if(!p.singleGrant || !p.beenGrantedOnce()){
                        p.onGranted.accept(activity,
                                ()->checkNextBlockingPermission(activity, requestCode));
                    }
                }
            }
        }
    }

    private void checkNextBlockingPermission(Activity activity, int requestCode){
        if(blockingRequestCode == requestCode){
            checkPermissionsBlocking(activity, remainingNames);
        }
    }

    public static BiConsumer<Activity, Procedure> empty = (a, p) -> p.perform();

    public static class Permission{
        public final String code;
        public final String name;
        public final String explanation;
        public final int requestCode;
        public final BiConsumer<Activity, Procedure> onGranted;
        public final BiConsumer<Activity, Procedure> onDenied;
        public final boolean singleGrant;
        private boolean grantedOnce = false;

        public Permission(String code, int requestCode, @Nullable String explanation, boolean singleGrant,
                          BiConsumer<Activity, Procedure> onGranted, BiConsumer<Activity, Procedure> onDenied){
            this.code = code;
            this.requestCode = requestCode;
            this.explanation = explanation;
            this.onGranted = onGranted;
            this.onDenied = onDenied;
            this.singleGrant = singleGrant;
            String[] tmp = code.split("\\.");
            name = tmp[tmp.length - 1].replaceAll("_", " ").toLowerCase();
        }

        public Permission(String name, String code, int requestCode, @Nullable String explanation,
                          boolean singleGrant, BiConsumer<Activity, Procedure> onGranted, BiConsumer<Activity, Procedure> onDenied){
            this.code = code;
            this.requestCode = requestCode;
            this.explanation = explanation;
            this.onGranted = onGranted;
            this.onDenied = onDenied;
            this.name = name;
            this.singleGrant = singleGrant;
        }

        public boolean beenGrantedOnce() {
            return grantedOnce;
        }
    }
}
