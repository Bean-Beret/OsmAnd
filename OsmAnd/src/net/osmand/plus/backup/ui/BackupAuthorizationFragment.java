package net.osmand.plus.backup.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.chooseplan.ChoosePlanFragment;
import net.osmand.plus.chooseplan.OsmAndFeature;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.inapp.InAppPurchaseHelper;
import net.osmand.plus.inapp.InAppPurchaseHelper.InAppPurchaseListener;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.widgets.dialogbutton.DialogButtonType;
import net.osmand.plus.widgets.dialogbutton.DialogButton;

public class BackupAuthorizationFragment extends BaseOsmAndFragment implements InAppPurchaseListener {

	public static final String TAG = BackupAuthorizationFragment.class.getSimpleName();

	private DialogButton signUpButton;
	private DialogButton signInButton;

	@ColorRes
	public int getStatusBarColorId() {
		AndroidUiHelper.setStatusBarContentColor(getView(), nightMode);
		return ColorUtilities.getActivityBgColorId(nightMode);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = themedInflater.inflate(R.layout.fragment_authorize_cloud, container, false);
		AndroidUtils.addStatusBarPadding21v(requireMyActivity(), view);

		signUpButton = view.findViewById(R.id.sign_up_button);
		signInButton = view.findViewById(R.id.sign_in_button);

		updateButtons();
		setupToolbar(view);

		return view;
	}

	private void setupToolbar(@NonNull View view) {
		TextView title = view.findViewById(R.id.toolbar_title);
		title.setText(R.string.backup_and_restore);

		View subtitle = view.findViewById(R.id.toolbar_subtitle);
		AndroidUiHelper.updateVisibility(subtitle, false);

		ImageView closeButton = view.findViewById(R.id.close_button);
		closeButton.setImageDrawable(getContentIcon(AndroidUtils.getNavigationIconResId(view.getContext())));
		closeButton.setOnClickListener(v -> {
			FragmentActivity activity = getActivity();
			if (activity != null) {
				activity.onBackPressed();
			}
		});
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.profile_button), false);
	}

	private void updateButtons() {
		boolean subscribed = InAppPurchaseHelper.isOsmAndProAvailable(app);
		if (subscribed) {
			setupAuthorizeButton(signUpButton, DialogButtonType.PRIMARY, R.string.register_opr_create_new_account, true);
		} else {
			signUpButton.setButtonType(DialogButtonType.PRIMARY);
			signUpButton.setTitleId(R.string.shared_string_get);
			signUpButton.setOnClickListener(v -> {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					ChoosePlanFragment.showInstance(activity, OsmAndFeature.OSMAND_CLOUD);
				}
			});
		}
		setupAuthorizeButton(signInButton, DialogButtonType.SECONDARY, R.string.register_opr_have_account, false);
	}

	private void setupAuthorizeButton(DialogButton button, DialogButtonType buttonType, @StringRes int textId, boolean signUp) {
		button.setButtonType(buttonType);
		button.setTitleId(textId);
		button.setOnClickListener(v -> {
			FragmentActivity activity = getActivity();
			if (activity != null) {
				AuthorizeFragment.showInstance(activity.getSupportFragmentManager(), signUp);
			}
		});
	}

	@Override
	public void onItemPurchased(String sku, boolean active) {
		updateButtons();
	}

	@Override
	public void onResume() {
		super.onResume();

		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.disableDrawer();
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.enableDrawer();
		}
	}

	@Nullable
	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	public static void showInstance(@NonNull FragmentManager manager) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			Fragment fragment = new BackupAuthorizationFragment();
			manager.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss();
		}
	}
}