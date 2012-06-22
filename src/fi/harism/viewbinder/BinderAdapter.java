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

import android.view.View;
import android.view.ViewGroup;

/**
 * Simple View provider interface.
 */
public interface BinderAdapter {

	/**
	 * Getter for individual Views. Container is the parent View and position is
	 * value between [0, getCount()].
	 */
	public View createView(ViewGroup container, int position);

	/**
	 * Return number of Views this adapter can provide.
	 * 
	 * @return Number of Views.
	 */
	public int getCount();

}
