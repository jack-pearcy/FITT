import Foundation

/// An error body from the FITT API (RFC 9457 problem details).
///
/// Validation failures carry one message per field. The server writes that map
/// under `properties.errors`, not at the top level.
public struct APIProblem: Decodable, Equatable, Sendable {
    public let status: Int?
    public let title: String?
    public let detail: String?
    public let fieldErrors: [String: String]

    public init(status: Int? = nil, title: String? = nil, detail: String? = nil, fieldErrors: [String: String] = [:]) {
        self.status = status
        self.title = title
        self.detail = detail
        self.fieldErrors = fieldErrors
    }

    private enum CodingKeys: String, CodingKey {
        case status, title, detail, properties
    }

    private struct Properties: Decodable {
        let errors: [String: String]?
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        status = try container.decodeIfPresent(Int.self, forKey: .status)
        title = try container.decodeIfPresent(String.self, forKey: .title)
        detail = try container.decodeIfPresent(String.self, forKey: .detail)
        fieldErrors = try container.decodeIfPresent(Properties.self, forKey: .properties)?.errors ?? [:]
    }
}
