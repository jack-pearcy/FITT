import Foundation
import Security

/// Where the access token is kept between launches.
public protocol TokenStore: Sendable {
    func read() throws -> String?
    func save(_ token: String) throws
    func clear() throws
}

public struct KeychainError: Error, Equatable, Sendable {
    public let status: OSStatus
}

/// Stores the token as a generic password item in the Keychain.
///
/// The item is readable only while the device is unlocked and never leaves this
/// device, so it is not restored onto another phone from a backup.
public struct KeychainTokenStore: TokenStore {
    private let service: String
    private let account: String

    public init(service: String = "dev.fittapp.fitt", account: String = "access-token") {
        self.service = service
        self.account = account
    }

    private var itemQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    public func read() throws -> String? {
        var query = itemQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainError(status: status) }
        guard let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    public func save(_ token: String) throws {
        // Delete then add, so the accessibility class is always the one set here,
        // including for an item written by an older build.
        try clear()
        var attributes = itemQuery
        attributes[kSecValueData as String] = Data(token.utf8)
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly

        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    public func clear() throws {
        let status = SecItemDelete(itemQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError(status: status)
        }
    }
}

/// Keeps the token in memory only. For previews and tests.
public final class InMemoryTokenStore: TokenStore, @unchecked Sendable {
    private let lock = NSLock()
    private var token: String?

    public init(token: String? = nil) {
        self.token = token
    }

    public func read() -> String? {
        lock.withLock { token }
    }

    public func save(_ token: String) {
        lock.withLock { self.token = token }
    }

    public func clear() {
        lock.withLock { token = nil }
    }
}
