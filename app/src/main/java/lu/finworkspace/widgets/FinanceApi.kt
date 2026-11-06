package lu.finworkspace.widgets

import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FinanceApi(private val settings: SecureSettings) {
    private fun request(path: String, method: String = "GET", body: String? = null, bearer: String? = null): String {
        val connection = URL(settings.server + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (status !in 200..299) throw ApiError(status, "HTTP $status")
            return response
        } finally { connection.disconnect() }
    }
    fun login(email: String, password: String) {
        val body = "username=${enc(email)}&password=${enc(password)}"
        val tokens = JSONObject(request("/api/auth/login", "POST", body))
        settings.accessToken = tokens.getString("access_token")
        settings.refreshToken = tokens.getString("refresh_token")
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun get(path: String): String {
        try { return request("/api$path", bearer = settings.accessToken) }
        catch (e: ApiError) {
            if (e.status != 401) throw e
            val refresh = settings.refreshToken
            if (refresh.isBlank()) throw e
            val tokens = try { JSONObject(request("/api/auth/refresh?refresh_token=${enc(refresh)}", "POST")) }
                catch (error: Exception) { settings.clear(); throw error }
            settings.accessToken = tokens.getString("access_token")
            settings.refreshToken = tokens.getString("refresh_token")
            return request("/api$path", bearer = settings.accessToken)
        }
    }
    fun accounts(): JSONArray = JSONArray(get("/accounts/?limit=100"))
    fun transactions(): JSONArray = JSONObject(get("/transactions/?page=1&per_page=8")).getJSONArray("items")
    class ApiError(val status: Int, message: String) : Exception(message)
}
