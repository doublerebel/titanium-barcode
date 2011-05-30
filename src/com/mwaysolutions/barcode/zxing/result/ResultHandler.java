/*
 * Copyright (c) 2011 by Double Rebel
 * http://www.doublerebel.com
 *
 *
 * Based on Zxing pre-3.6 (SVN Trunk Rev 1770)
 * Copyright (C) 2010 ZXing authors
 * http://code.google.com/p/zxing/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.doublerebel.barcode.zxing.result;

import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;

import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ResultParser;

/**
 * Wrapper class for results from barcode scanner
 * 
 * @author sven@roothausen.de (Sven Pfleiderer)
 * @author charles@doublerebel.com (Charles Phillips)
 *
 */

public class ResultHandler {

    private static final String LCAT = "TitaniumBarcodeResultHandler";
	private static final boolean DBG = TiConfig.LOGD;

   	private final ParsedResult mResult;

	public ResultHandler(final Result rawResult) {
		this.mResult = parseResult(rawResult);
		if (DBG) {
            Log.d(LCAT, "Got new data: " + rawResult);
        }
	}

	public Object getType() {
		return mResult.getType();
	}

	public CharSequence getDisplayContents() {
		return mResult.getDisplayResult();
	}

	private ParsedResult parseResult(Result rawResult) {
		return ResultParser.parseResult(rawResult);
	}

}
