package com.banfftech.kupangpromote.common;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;

import net.sf.json.JSON;
import net.sf.json.JSONObject;

/*
 * Copy By CloudCard 
 *
 * @date 2017年4月26日
 * 
 *
 */
public class CommonEvents {

	public static final String module = CommonEvents.class.getName();

	public static final String ERROR_MSG_KEY = "_ERROR_MESSAGE_";
	public static final String ERROR_MSG_LIST_KEY = "_ERROR_MESSAGE_LIST_";

	// 几个简单的错误码
	public static final String CODE_SUCC = "200"; // 成功
	public static final String CODE_INTERNAL_ERROR = "300"; // 内部服务错误
	public static final String CODE_NEED_LOGIN = "401"; // 需要登录
	public static final String CODE_OLD_VERSION = "402"; // 版本太低
	public static final String CODE_BIZ_ERROR = "500"; // 其他业务错误

	public static final String MSG_SUCC = "success";

	private static final Pattern NEED_LOGIN_MSG_PATTERN = Pattern
			.compile("^User authorization is required for this service.+?");

	static final String[] ignoreAttrs = new String[] { // Removed for security
														// reason;
														// _ERROR_MESSAGE_ is
														// kept
	"javax.servlet.request.key_size", "_CONTEXT_ROOT_",
			"_FORWARDED_FROM_SERVLET_", "javax.servlet.request.ssl_session",
			"javax.servlet.request.ssl_session_id", "multiPartMap",
			"javax.servlet.request.cipher_suite", "targetRequestUri",
			"_SERVER_ROOT_URL_", "_CONTROL_PATH_", "thisRequestUri" };

	public static String jsonResponseForCloudCard(HttpServletRequest request,
			HttpServletResponse response) {
		// pull out the service response from the request attribute

		Map<String, Object> attrMap = UtilHttp.getJSONAttributeMap(request);

		for (String ignoreAttr : ignoreAttrs) {
			if (attrMap.containsKey(ignoreAttr)) {
				attrMap.remove(ignoreAttr);
			}
		}

		String msg = MSG_SUCC;
		String code = CODE_SUCC;
		if (attrMap.containsKey(ERROR_MSG_KEY)
				&& UtilValidate.isNotEmpty(attrMap.get(ERROR_MSG_KEY))) {
			msg = (String) attrMap.get(ERROR_MSG_KEY);
			Matcher matcher = NEED_LOGIN_MSG_PATTERN.matcher(msg);
			if (matcher.find()) {
				code = CODE_NEED_LOGIN;
			} else {
				code = CODE_BIZ_ERROR;
			}
			attrMap.remove(ERROR_MSG_KEY);

		} else if (attrMap.containsKey(ERROR_MSG_LIST_KEY)
				&& UtilValidate.isNotEmpty(attrMap.get(ERROR_MSG_LIST_KEY))) {
			List<String> msgLst = UtilGenerics.checkList(attrMap
					.get(ERROR_MSG_LIST_KEY));
			for (String m : msgLst) {
				msg += m;
				Matcher matcher = NEED_LOGIN_MSG_PATTERN.matcher(m);
				if (matcher.find()) {
					code = CODE_NEED_LOGIN;
					break;
				} else {
					code = CODE_BIZ_ERROR;
				}
			}

			attrMap.remove(ERROR_MSG_LIST_KEY);
		}
		attrMap.put("code", code);
		attrMap.put("msg", msg);

		// create a JSON Object for return
		JSONObject json = JSONObject.fromObject(attrMap);

		if (Debug.infoOn()) {
			String token = (String) attrMap.remove("token");
			StringBuilder logsb = new StringBuilder(500);
			logsb.append(request.getPathInfo());
			logsb.append(System.getProperty("line.separator"));
			logsb.append("output:");
			logsb.append(System.getProperty("line.separator"));
			logsb.append(UtilMisc.printMap(attrMap));
			if (UtilValidate.isNotEmpty(token)) {
				logsb.append("For security reasons, a token field was removed when this log was logged.");
				logsb.append(System.getProperty("line.separator"));
			}
			Debug.logInfo(logsb.toString(), "cloudcard");
		}

		writeJSONtoResponse(json, request.getMethod(), response);
		return "success";
	}

	private static void writeJSONtoResponse(JSON json, String httpMethod,
			HttpServletResponse response) {
		String jsonStr = json.toString();
		if (jsonStr == null) {
			Debug.logError("JSON Object was empty; fatal error!", module);
			return;
		}

		// This was added for security reason (OFBIZ-5409), you might need to
		// remove the "//" prefix when handling the JSON response
		// Though normally you simply have to access the data you want, so
		// should not be annoyed by the "//" prefix
		if ("GET".equalsIgnoreCase(httpMethod)) {
			Debug.logWarning(
					"for security reason (OFBIZ-5409) the the '//' prefix was added handling the JSON response.  "
							+ "Normally you simply have to access the data you want, so should not be annoyed by the '//' prefix."
							+ "You might need to remove it if you use Ajax GET responses (not recommended)."
							+ "In case, the util.js scrpt is there to help you",
					module);
			jsonStr = "//" + jsonStr;
		}

		// set the X-JSON content type
		response.setContentType("application/x-json");
		// jsonStr.length is not reliable for unicode characters
		try {
			response.setContentLength(jsonStr.getBytes("UTF8").length);
		} catch (UnsupportedEncodingException e) {
			Debug.logError("Problems with Json encoding: " + e, module);
		}

		// return the JSON String
		Writer out;
		try {
			out = response.getWriter();
			out.write(jsonStr);
			out.flush();
		} catch (IOException e) {
			Debug.logError(e, module);
		}
	}
	
	
}
