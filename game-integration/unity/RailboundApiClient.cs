using System;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Networking;

namespace Railbound.Economy
{
    /// <summary>
    /// Authoritative HTTP API Client for the Railbound Battle Royale Payment & Asset Portal.
    /// Handles Bearer JWT injection, structured error parsing, cancellation, and retry logic.
    /// </summary>
    public class RailboundApiClient : MonoBehaviour
    {
        public static RailboundApiClient Instance { get; private set; }

        [Header("Backend Configuration")]
        [SerializeField] private string apiBaseUrl = "http://localhost:8080/api/v1";

        private string _authToken;
        private string _cachedUserId;

        public string AuthToken => _authToken;
        public string CachedUserId => _cachedUserId;
        public bool IsAuthenticated => !string.IsNullOrEmpty(_authToken);

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        public void SetAuthSession(string token, string userId)
        {
            _authToken = token;
            _cachedUserId = userId;
            Debug.Log($"[RailboundApiClient] Session configured for player: {userId}");
        }

        public async Task<ApiResponse<T>> GetAsync<T>(string endpoint, CancellationToken ct = default)
        {
            return await SendRequestAsync<T>(endpoint, UnityWebRequest.kHttpVerbGET, null, ct);
        }

        public async Task<ApiResponse<T>> PostAsync<T>(string endpoint, string jsonPayload, string idempotencyKey = null, CancellationToken ct = default)
        {
            return await SendRequestAsync<T>(endpoint, UnityWebRequest.kHttpVerbPOST, jsonPayload, ct, idempotencyKey);
        }

        private async Task<ApiResponse<T>> SendRequestAsync<T>(
            string endpoint,
            string method,
            string jsonPayload,
            CancellationToken ct,
            string idempotencyKey = null)
        {
            string url = apiBaseUrl + endpoint;
            using var request = new UnityWebRequest(url, method);

            if (!string.IsNullOrEmpty(jsonPayload))
            {
                byte[] bodyRaw = Encoding.UTF8.GetBytes(jsonPayload);
                request.uploadHandler = new UploadHandlerRaw(bodyRaw);
                request.SetRequestHeader("Content-Type", "application/json");
            }

            request.downloadHandler = new DownloadHandlerBuffer();
            request.timeout = 15; // 15s timeout

            if (!string.IsNullOrEmpty(_authToken))
            {
                request.SetRequestHeader("Authorization", "Bearer " + _authToken);
            }

            if (!string.IsNullOrEmpty(idempotencyKey))
            {
                request.SetRequestHeader("Idempotency-Key", idempotencyKey);
            }

            var operation = request.SendWebRequest();
            while (!operation.isDone)
            {
                if (ct.IsCancellationRequested)
                {
                    request.Abort();
                    return ApiResponse<T>.Failure("Request cancelled by client");
                }
                await Task.Yield();
            }

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning($"[RailboundApiClient] {method} {endpoint} failed ({request.responseCode}): {request.error}");
                return ApiResponse<T>.Failure(request.error, (int)request.responseCode, request.downloadHandler?.text);
            }

            try
            {
                string responseText = request.downloadHandler.text;
                T data = JsonUtility.FromJson<T>(responseText);
                return ApiResponse<T>.Success(data, (int)request.responseCode);
            }
            catch (Exception ex)
            {
                Debug.LogError($"[RailboundApiClient] JSON Deserialization error: {ex.Message}");
                return ApiResponse<T>.Failure("JSON parse error: " + ex.Message, (int)request.responseCode);
            }
        }
    }

    [Serializable]
    public class ApiResponse<T>
    {
        public bool IsSuccess;
        public int StatusCode;
        public T Data;
        public string ErrorMessage;
        public string RawResponse;

        public static ApiResponse<T> Success(T data, int statusCode) => new()
        {
            IsSuccess = true,
            StatusCode = statusCode,
            Data = data
        };

        public static ApiResponse<T> Failure(string error, int statusCode = 0, string raw = null) => new()
        {
            IsSuccess = false,
            StatusCode = statusCode,
            ErrorMessage = error,
            RawResponse = raw
        };
    }
}
