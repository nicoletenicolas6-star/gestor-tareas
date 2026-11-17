package com.customautosys.saf_mediastore;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class SafMediastore extends CordovaPlugin implements ValueCallback<String>{
	protected CallbackContext callbackContext;
	protected HashMap<String,String> saveFileData=new HashMap<>();
	protected CordovaInterface cordovaInterface;
	protected CordovaWebView cordovaWebView;

	public enum Action {
		selectFolder,
		selectFile,
		openFolder,
		openFile,
		readFile,
		writeFile,
		saveFile
	}

	@Override
	public void initialize(CordovaInterface cordovaInterface,CordovaWebView cordovaWebView){
		this.cordovaInterface=cordovaInterface;
		this.cordovaWebView=cordovaWebView;
	}

	@Override
	public boolean execute(String action,JSONArray args,CallbackContext callbackContext)throws JSONException{
		Method method=null;
		try{
			method=this.getClass().getMethod(action,JSONArray.class,CallbackContext.class);
		}catch(Exception e){
			debugLog(e);
		}
		if(method==null||!Modifier.isPublic(method.getModifiers()))return false;
		try{
			return (Boolean)method.invoke(this,args,callbackContext);
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean selectFolder(JSONArray args,CallbackContext callbackContext)throws JSONException{
		Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		String initialFolder=null;
		try{
			if(!args.isNull(0))initialFolder=args.getString(0);
		}catch(JSONException e){
			debugLog(e);
		}
		if(initialFolder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,initialFolder);
		this.callbackContext=callbackContext;
		cordovaInterface.startActivityForResult(this,intent,Action.selectFolder.ordinal());
		return true;
	}

	public boolean selectFile(JSONArray args,CallbackContext callbackContext)throws JSONException{
		Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
		String initialFolder=null;
		try{
			if(!args.isNull(0))initialFolder=args.getString(0);
		}catch(JSONException e){
			debugLog(e);
		}
		if(initialFolder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,initialFolder);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"*/*"});
		intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		this.callbackContext=callbackContext;
		cordovaInterface.startActivityForResult(this,Intent.createChooser(intent,"Select File"),Action.selectFile.ordinal());
		return true;
	}

	public boolean openFolder(JSONArray args,CallbackContext callbackContext){
		try{
			Intent intent=new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse(args.getString(0)),DocumentsContract.Document.MIME_TYPE_DIR);
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,Intent.createChooser(intent,"Open folder"),Action.openFolder.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean openFile(JSONArray args,CallbackContext callbackContext){
		try{
			String uriString=args.getString(0);
			Uri uri=Uri.parse(uriString);
			Intent intent=new Intent(Intent.ACTION_VIEW);
			String mimeType=cordovaInterface.getContext().getContentResolver().getType(uri);
			if(mimeType==null)mimeType="*/*";
			intent.setDataAndType(uri,mimeType);
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,intent,Action.openFile.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	@RequiresApi(api=Build.VERSION_CODES.Q)
	public boolean readFile(JSONArray args,CallbackContext callbackContext){
		try(
			InputStream inputStream=cordovaInterface.getContext().getContentResolver().openInputStream(Uri.parse(args.getString(0)));
			ByteArrayOutputStream byteArrayOutputStream=new ByteArrayOutputStream()
		){
			FileUtils.copy(inputStream, byteArrayOutputStream);
			callbackContext.success(byteArrayOutputStream.toByteArray());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean writeFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String filename=params.getString("filename");
			String mimeType=MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(filename.lastIndexOf('.')+1));
			if(mimeType==null)mimeType="*/*";
			String folder=null;
			try{
				if(!params.isNull("folder"))folder=params.getString("folder");
			}catch(Exception e){
				debugLog(e);
			}
			String subFolder="";
			try{
				if(!params.isNull("subFolder"))subFolder=params.getString("subFolder");
			}catch(Exception e){
				debugLog(e);
			}
			Uri uri=null;
			if(folder!=null&&!folder.trim().equals("")){
				DocumentFile documentFile=DocumentFile.fromTreeUri(
					cordovaInterface.getContext(),
					Uri.parse(folder)
				);
				if(subFolder!=null){
					String subFolders[]=subFolder.split("/");
					for(int i=0;i<subFolders.length;++i){
						DocumentFile subFolderDocumentFile=null;
						for(DocumentFile subFile:documentFile.listFiles()){
							if(subFile.isDirectory()&&subFile.getName().equals(subFolder)){
								subFolderDocumentFile=subFile;
								break;
							}
						}
						documentFile=subFolderDocumentFile!=null?subFolderDocumentFile:documentFile.createDirectory(subFolders[i]);
					}
				}
				DocumentFile file=null;
				for(DocumentFile subFile:documentFile.listFiles()){
					if(!subFile.isDirectory()&&subFile.getName().equals(filename)){
						file=subFile;
						break;
					}
				}
				uri=(file!=null?file:documentFile.createFile(
					mimeType,
					filename
				)).getUri();
}else{
			ContentResolver contentResolver=cordovaInterface.getContext().getContentResolver();
			ContentValues contentValues=new ContentValues();
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE,mimeType);
			if(!subFolder.startsWith("/"))subFolder="/"+subFolder;
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+subFolder);
				uri=contentResolver.insert(MediaStore.Files.getContentUri("external"),contentValues);
			}else{
				// Android <= 9: escribir por ruta directa (permiso WRITE_EXTERNAL_STORAGE).
				java.io.File dir=new java.io.File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
					subFolder.replace('/','/')
				);
				if(!dir.exists()&&!dir.mkdirs())throw new Exception("No se pudo crear la carpeta: "+dir.getAbsolutePath());
				java.io.File archivo=new java.io.File(dir,filename);
				try(java.io.OutputStream fos=new java.io.FileOutputStream(archivo)){
					fos.write(Base64.decode(params.getString("data"),Base64.DEFAULT));
				}
				uri=Uri.fromFile(archivo);
			}
		}
			try(OutputStream outputStream=cordovaInterface.getContext().getContentResolver().openOutputStream(uri,"wt")){
				outputStream.write(Base64.decode(params.getString("data"),Base64.DEFAULT));
			}
			callbackContext.success(uri.toString());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean overwriteFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			Uri uri=Uri.parse(params.getString("uri"));
			try(OutputStream outputStream=cordovaInterface.getContext().getContentResolver().openOutputStream(uri,"wt")){
				outputStream.write(Base64.decode(params.getString("data"),Base64.DEFAULT));
			}
			callbackContext.success(uri.toString());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean saveFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			saveFileData.put(callbackContext.getCallbackId(),params.getString("data"));
			Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			String filename=null;
			try{
				if(!params.isNull("filename"))filename=params.getString("filename");
			}catch(Exception e){
				debugLog(e);
			}
			if(filename!=null){
				String mimeType=MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(filename.lastIndexOf('.')+1));
				if(mimeType==null)mimeType="*/*";
				intent.setType(mimeType);
				intent.putExtra(Intent.EXTRA_TITLE,filename);
			}
			String folder=null;
			try{
				if(!params.isNull("folder"))folder=params.getString("folder");
			}catch(Exception e){
				debugLog(e);
			}
			if(folder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,folder);
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,intent,Action.saveFile.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean deleteFile(JSONArray args,CallbackContext callbackContext){
		try{
			callbackContext.success(cordovaInterface.getContext().getContentResolver().delete(Uri.parse(args.getString(0)),null));
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean getFileName(JSONArray args,CallbackContext callbackContext){
		try(Cursor cursor=cordovaInterface.getContext().getContentResolver().query(Uri.parse(args.getString(0)),null,null,null,null)){
			if(cursor!=null&&cursor.moveToFirst()){
				callbackContext.success(cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
				return true;
			}
			callbackContext.error(args.getString(0)+" not found");
			return false;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	/**
	 * Busca en MediaStore (carpeta Download) un archivo que creó la propia app,
	 * por nombre y subcarpeta, y devuelve su content:// URI. No pide permisos ni
	 * abre selectores: la fila de MediaStore sigue vinculada a esta app, así que
	 * sirve para reencontrar el marcador de licencia tras una reinstalación.
	 * La subcarpeta puede llevar o no barra final (writeFile la guarda sin ella).
	 */
	public boolean findDownloadsFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String filename=params.getString("filename");
			String subFolder="";
			try{
				if(!params.isNull("subFolder"))subFolder=params.getString("subFolder");
			}catch(JSONException e){
				debugLog(e);
			}
			while(subFolder.startsWith("/"))subFolder=subFolder.substring(1);
			while(subFolder.endsWith("/"))subFolder=subFolder.substring(0,subFolder.length()-1);
			String relSin= "Download/"+(subFolder.isEmpty()?"":subFolder);          // tal y como lo escribe writeFile
			String relCon=relSin.isEmpty()?"/":(relSin+"/");                        // tolera barra final
			Uri collection=MediaStore.Files.getContentUri("external");
			String selection;
			String[] selArgs;
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
				selection="("+MediaStore.MediaColumns.RELATIVE_PATH+"=? OR "+MediaStore.MediaColumns.RELATIVE_PATH+"=?) AND "+MediaStore.MediaColumns.DISPLAY_NAME+"=?";
				selArgs=new String[]{relSin,relCon,filename};
			}else{
				selection="("+MediaStore.MediaColumns.DATA+" LIKE ? OR "+MediaStore.MediaColumns.DATA+" LIKE ?) AND "+MediaStore.MediaColumns.DISPLAY_NAME+"=?";
				selArgs=new String[]{"%/"+relSin+"%","%/"+relCon+"%",filename};
			}
			try(Cursor cursor=cordovaInterface.getContext().getContentResolver().query(
					collection,
					new String[]{MediaStore.MediaColumns._ID},
					selection,
					selArgs,
					MediaStore.MediaColumns.DATE_ADDED+" DESC")){
				if(cursor!=null&&cursor.moveToFirst()){
					long id=cursor.getLong(0);
					callbackContext.success(Uri.withAppendedPath(collection,Long.toString(id)).toString());
					return true;
				}
			}
			callbackContext.error("Not found: "+relSin+"/"+filename);
			return false;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean getUri(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String folder=null;
			try{
				if(!params.isNull("folder"))params.getString("folder");
			}catch(Exception e){
				debugLog(e);
			}
			DocumentFile documentFile=DocumentFile.fromTreeUri(
					cordovaInterface.getContext(),
					Uri.parse(folder)
			);
			String subFolder=null;
			try {
				if(!params.isNull("subFolder"))subFolder=params.getString("subFolder");
			}catch(Exception e){
				debugLog(e);
			}
			if(subFolder!=null){
				String subFolders[]=subFolder.split("/");
				for(int i=0;i<subFolders.length;++i){
					DocumentFile subFolderDocumentFile=null;
					for(DocumentFile subFile:documentFile.listFiles()){
						if(subFile.isDirectory()&&subFile.getName().equals(subFolder)){
							subFolderDocumentFile=subFile;
							break;
						}
					}
					if(subFolderDocumentFile==null)throw new Exception("Subfolder not found in "+folder+": "+subFolder);
					documentFile=subFolderDocumentFile;
				}
			}
			String filename=null;
			try {
				if(!params.isNull("filename"))filename=params.getString("filename");
			}catch(Exception e){
				debugLog(e);
			}
			if(filename==null){
				callbackContext.success(documentFile.getUri().toString());
				return true;
			}
			DocumentFile file=null;
			for(DocumentFile subFile:documentFile.listFiles()){
				if(!subFile.isDirectory()&&subFile.getName().equals(filename)){
					file=subFile;
					break;
				}
			}
			if(file==null)throw new Exception("File not found in "+subFolder+" of "+folder+": "+filename);
			callbackContext.success(file.getUri().toString());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	@Override
	public void onActivityResult(int requestCode,int resultCode,Intent intent){
		if(callbackContext==null){
			debugLog("callbackContext==null in onActivityResult");
			return;
		}
		if(requestCode<0||requestCode>=Action.values().length){
			callbackContext.error(debugLog("Invalid request code: "+requestCode));
			return;
		}
		switch(Action.values()[requestCode]){
			case selectFolder:
			case selectFile:
				if(resultCode!=Activity.RESULT_OK){
					callbackContext.error(debugLog("Cancelled"));
					return;
				}
				callbackContext.success(intent.getDataString());
				break;
			case openFolder:
			case openFile:
				callbackContext.success();
				break;
			case saveFile:
				if(resultCode!=Activity.RESULT_OK){
					callbackContext.error(debugLog("Cancelled"));
					return;
				}
				String data=saveFileData.remove(callbackContext.getCallbackId());
				if(data==null){
					callbackContext.error(debugLog("No saveFileData in onActivityResult"));
					break;
				}
				try(OutputStream outputStream=cordovaInterface.getContext().getContentResolver().openOutputStream(intent.getData(),"wt")){
					outputStream.write(Base64.decode(data,Base64.DEFAULT));
					callbackContext.success(intent.getDataString());
				}catch(Exception e){
					callbackContext.error(debugLog(e));
				}
				break;
			default:
				callbackContext.error(debugLog("Invalid request code: "+Action.values()[requestCode].toString()));
		}
	}

	public String debugLog(Throwable throwable){
		try {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			throwable.printStackTrace(printWriter);
			String stackTrace=stringWriter.toString();
			Log.d(throwable.getLocalizedMessage(),stackTrace,throwable);
			cordovaWebView.getEngine().evaluateJavascript(
				"console.log('" +stackTrace.replace(
					"'",
					"\\'"
				).replace(
					"\n",
					"\\n"
				).replace(
					"\t",
					"\\t"
				)+"');",
				this
			);
			printWriter.close();
			stringWriter.close();
			return stackTrace;
		}catch(Exception e){
			e.printStackTrace();
			return "";
		}
	}

	public String debugLog(String message){
		Log.d(getClass().getName(),message);
		cordovaWebView.getEngine().evaluateJavascript(
			"console.log('" +message.replace(
				"'",
				"\\'"
			).replace(
				"\n",
				"\\n"
			).replace(
				"\t",
				"\\t"
			)+"');",
			this
		);
		return message;
	}

	// ============================================================================
	// Acceso a todos los archivos (MANAGE_EXTERNAL_STORAGE): única vía que
	// sobrevive a desinstalar/reinstalar en Android 11+ para el marcador oculto:
	// el marcador se escribe/lee como ARCHIVO FÍSICO en Download/.MiTiendaPOS/.
	// El permiso se concede una sola vez en Ajustes y persiste entre instalaciones.
	// ============================================================================

	/** Devuelve "si"/"no" según la app tenga o no "Acceso a todos los archivos". */
	public boolean tieneAccesoTodosArchivos(JSONArray args,CallbackContext callbackContext){
		try{
			boolean ok=Build.VERSION.SDK_INT>=Build.VERSION_CODES.R&&Environment.isExternalStorageManager();
			callbackContext.success(ok?"si":"no");
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	/** Abre Ajustes → "Acceso a todos los archivos" para esta app. */
	public boolean solicitarAccesoTodosArchivos(JSONArray args,CallbackContext callbackContext){
		try{
			Intent intent=new Intent(
				Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
				Uri.parse("package:"+cordovaInterface.getContext().getPackageName())
			);
			cordovaInterface.getActivity().startActivity(intent);
			callbackContext.success("ok");
			return true;
		}catch(Exception e){
			try{
				Intent intent=new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
				cordovaInterface.getActivity().startActivity(intent);
				callbackContext.success("ok");
				return true;
			}catch(Exception e2){
				try{
					// Fallback universal: abre la página de info de la app (funciona en TODO dispositivo)
					Intent intent=new Intent(
						Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
						Uri.parse("package:"+cordovaInterface.getContext().getPackageName())
					);
					cordovaInterface.getActivity().startActivity(intent);
					callbackContext.success("ok");
					return true;
				}catch(Exception e3){
					callbackContext.error(debugLog(e3));
					return false;
				}
			}
		}
	}

	/** Escribe el marcador como archivo físico directo (sobrevive a desinstalar). */
	public boolean writeLegacyFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String filename=params.getString("filename");
			String subFolder="";
			try{
				if(!params.isNull("subFolder"))subFolder=params.getString("subFolder");
			}catch(JSONException e){
				debugLog(e);
			}
			while(subFolder.startsWith("/"))subFolder=subFolder.substring(1);
			while(subFolder.endsWith("/"))subFolder=subFolder.substring(0,subFolder.length()-1);
			java.io.File dir=new java.io.File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
				subFolder
			);
			if(!dir.exists()&&!dir.mkdirs())throw new Exception("No se pudo crear: "+dir.getAbsolutePath());
			java.io.File archivo=new java.io.File(dir,filename);
			try(java.io.FileOutputStream fos=new java.io.FileOutputStream(archivo)){
				if(params.has("data")){
					fos.write(Base64.decode(params.getString("data"),Base64.DEFAULT));
				}
			}
			callbackContext.success(archivo.getAbsolutePath());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	/** Lee el marcador físico por ruta. Devuelve su contenido como texto. */
	public boolean readLegacyFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String filename=params.getString("filename");
			String subFolder="";
			try{
				if(!params.isNull("subFolder"))subFolder=params.getString("subFolder");
			}catch(JSONException e){
				debugLog(e);
			}
			while(subFolder.startsWith("/"))subFolder=subFolder.substring(1);
			while(subFolder.endsWith("/"))subFolder=subFolder.substring(0,subFolder.length()-1);
			java.io.File archivo=new java.io.File(
				new java.io.File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
					subFolder
				),
				filename
			);
			if(!archivo.exists())throw new Exception("Not found: Download/"+(subFolder.isEmpty()?"":subFolder+"/")+filename);
			try(java.io.FileInputStream fis=new java.io.FileInputStream(archivo);
				java.io.ByteArrayOutputStream bos=new java.io.ByteArrayOutputStream()){
				byte[] buf=new byte[4096];
				int n;
				while((n=fis.read(buf))!=-1)bos.write(buf,0,n);
				callbackContext.success(bos.toString("UTF-8"));
			}
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	@Override
	public void onReceiveValue(String value){}
}