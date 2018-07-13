package com.sarriaroman.PhotoViewer;

import uk.co.senab.photoview.PhotoViewAttacher;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.support.media.ExifInterface;


public class PhotoActivity extends Activity {
	private PhotoViewAttacher mAttacher;

	private ImageView photo;
	private String imageUrl;

	private ImageButton closeBtn;
	private ImageButton shareBtn;

	private TextView titleTxt;

	private JSONObject options;
	private int shareBtnVisibility;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(getApplication().getResources().getIdentifier("activity_photo", "layout", getApplication().getPackageName()));

		// Load the Views
		findViews();

		try {
			options = new JSONObject(this.getIntent().getStringExtra("options"));
			shareBtnVisibility = options.getBoolean("share") ? View.VISIBLE : View.INVISIBLE;
		} catch(JSONException exception) {
			shareBtnVisibility = View.VISIBLE;
		}
		shareBtn.setVisibility(shareBtnVisibility);

		// Change the Activity Title
		String actTitle = this.getIntent().getStringExtra("title");
		if( !actTitle.equals("") ) {
			titleTxt.setText(actTitle);
		}

		// Set Button Listeners
		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		shareBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Uri bmpUri = getLocalBitmapUri(photo);

				if (bmpUri != null) {
					Intent sharingIntent = new Intent(Intent.ACTION_SEND);

					sharingIntent.setType("image/*");
					sharingIntent.putExtra(Intent.EXTRA_STREAM, bmpUri);

					startActivity(Intent.createChooser(sharingIntent, "Share"));
				}
			}
		});


		loadImage(PhotoViewer.url);
	}

	/**
	 * Find and Connect Views
	 *
	 */
	private void findViews() {
		// Buttons first
		closeBtn = (ImageButton) findViewById( getApplication().getResources().getIdentifier("closeBtn", "id", getApplication().getPackageName()) );
		shareBtn = (ImageButton) findViewById( getApplication().getResources().getIdentifier("shareBtn", "id", getApplication().getPackageName()) );

		// Photo Container
		photo = (ImageView) findViewById( getApplication().getResources().getIdentifier("photoView", "id", getApplication().getPackageName()) );
		mAttacher = new PhotoViewAttacher(photo);

		// Title TextView
		titleTxt = (TextView) findViewById( getApplication().getResources().getIdentifier("titleTxt", "id", getApplication().getPackageName()) );
	}

	/**
	 * Get the current Activity
	 *
	 * @return
	 */
	private Activity getActivity() {
		return this;
	}

	/**
	 * Hide Loading when showing the photo. Update the PhotoView Attacher
	 */
	private void hideLoadingAndUpdate() {
		photo.setVisibility(View.VISIBLE);

		shareBtn.setVisibility(shareBtnVisibility);

		mAttacher.update();
	}

	/**
	 * Load the image using Picasso
	 *
	 */
	private void loadImage(String url) {
		// create Picasso.Builder object
		Picasso.Builder picassoBuilder = new Picasso.Builder(getActivity().getApplicationContext());

		// add our custom eat foody request handler (see below for full class)
		picassoBuilder.addRequestHandler(new DataRequestHandler());

		// Picasso.Builder creates the Picasso object to do the actual requests
		Picasso picasso = picassoBuilder.build();

		picasso.load(url)
				.fit()
				.centerInside()
				.into(photo, new com.squareup.picasso.Callback() {
					@Override
					public void onSuccess() {
						hideLoadingAndUpdate();
					}

					@Override
					public void onError() {
						Toast.makeText(getActivity(), "Error loading image.", Toast.LENGTH_LONG).show();

						finish();
					}
				});
	}

	/**
	 * Create Local Image due to Restrictions
	 *
	 * @param imageView
	 *
	 * @return
	 */
	public Uri getLocalBitmapUri(ImageView imageView) {
		Drawable drawable = imageView.getDrawable();
		Bitmap bmp = null;

		if (drawable instanceof BitmapDrawable){
			bmp = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
		} else {
			return null;
		}

		// Store image to default external storage directory
		Uri bmpUri = null;
		try {
			File file =  new File(
					Environment.getExternalStoragePublicDirectory(
							Environment.DIRECTORY_DOWNLOADS
					), "share_image_" + System.currentTimeMillis() + ".png");

			file.getParentFile().mkdirs();

			FileOutputStream out = new FileOutputStream(file);
			bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
			out.close();

			bmpUri = Uri.fromFile(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bmpUri;
	}

	public class DataRequestHandler extends RequestHandler {

		private static final String DATA_SCHEME = "data";

		@Override
		public boolean canHandleRequest(Request data) {
			String scheme = data.uri.getScheme();
			return scheme.equalsIgnoreCase(DATA_SCHEME);
		}

		@Override
		public Result load(Request request, int networkPolicy) throws IOException {

			String uri = request.uri.toString();
			String imageDataBytes = uri.substring(uri.indexOf(",") + 1);
			byte[] bytes = Base64.decode(imageDataBytes.getBytes(), Base64.DEFAULT);

			ExifInterface exif = null;
			try {
				exif = new ExifInterface(new ByteArrayInputStream(bytes));
			} catch (IOException e) {
				e.printStackTrace();
			}
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_UNDEFINED);


			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
			options.inSampleSize = calculateInSampleSize(options, photo.getHeight(), photo.getWidth());
			options.inJustDecodeBounds = false;

			Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
			if(orientation != 0){
				bitmap = rotateBitmap(bitmap, orientation);
			}
			if (bitmap == null) return null;

			return new Result(bitmap, Picasso.LoadedFrom.NETWORK);
		}
	}

	public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

		Matrix matrix = new Matrix();
		switch (orientation) {
			case ExifInterface.ORIENTATION_NORMAL:
				return bitmap;
			case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
				matrix.setScale(-1, 1);
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				matrix.setRotate(180);
				break;
			case ExifInterface.ORIENTATION_FLIP_VERTICAL:
				matrix.setRotate(180);
				matrix.postScale(-1, 1);
				break;
			case ExifInterface.ORIENTATION_TRANSPOSE:
				matrix.setRotate(90);
				matrix.postScale(-1, 1);
				break;
			case ExifInterface.ORIENTATION_ROTATE_90:
				matrix.setRotate(90);
				break;
			case ExifInterface.ORIENTATION_TRANSVERSE:
				matrix.setRotate(-90);
				matrix.postScale(-1, 1);
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				matrix.setRotate(-90);
				break;
			default:
				return bitmap;
		}
		try {
			Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
			bitmap.recycle();
			return bmRotated;
		}
		catch (OutOfMemoryError e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Figure out what ratio we can load our image into memory at while still being bigger than
	 * our desired width and height
	 *
	 * @param options
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	private static int calculateInSampleSize(
			BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) >= reqHeight
					&& (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

}
