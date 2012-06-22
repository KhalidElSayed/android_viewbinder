/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.viewbinder;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/**
 * Simple example Activity.
 */
public class BinderActivity extends Activity implements BinderAdapter {

	// Predefined layouts used for demonstration purposes.
	private static final int[] LAYOUT_IDS = { R.layout.layout1,
			R.layout.layout2, R.layout.layout3 };

	@Override
	public View createView(ViewGroup container, int position) {
		return getLayoutInflater().inflate(LAYOUT_IDS[position], null);
	}

	@Override
	public int getCount() {
		return LAYOUT_IDS.length;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Assign adapter to BinderView.
		BinderView binder = (BinderView) findViewById(R.id.binder);
		binder.setAdapter(this);
	}
}