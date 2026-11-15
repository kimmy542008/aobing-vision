import SwiftUI
import AVKit

struct Catalog: Decodable { let list: [Film] }
struct Film: Decodable, Identifiable {
    let vod_id: Int
    let vod_name: String
    let vod_pic: String?
    let vod_play_url: String?
    var id: Int { vod_id }
}
struct Episode: Identifiable {
    let id: Int
    let name: String
    let url: URL
}
struct Playback: Identifiable {
    let id = UUID()
    let url: URL
}
@MainActor final class Library: ObservableObject {
    @Published var films: [Film] = []
    @Published var error = ""
    @Published var loading = false
    private var request = UUID()
    func load(api: String, search: String) async {
        let ticket = UUID()
        request = ticket
        loading = true
        error = ""
        defer { if request == ticket { loading = false } }
        do {
            guard var components = URLComponents(string: api),
                  ["http", "https"].contains(components.scheme ?? "") else {
                throw URLError(.badURL)
            }
            var queries = components.queryItems ?? []
            queries.removeAll { ["ac", "wd"].contains($0.name) }
            queries += [URLQueryItem(name: "ac", value: "detail"),
                        URLQueryItem(name: "wd", value: search)]
            components.queryItems = queries
            guard let url = components.url else { throw URLError(.badURL) }
            var query = URLRequest(url: url)
            query.timeoutInterval = 20
            let (data, response) = try await URLSession.shared.data(for: query)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }
            let result = try JSONDecoder().decode(Catalog.self, from: data)
            guard request == ticket else { return }
            films = result.list
        } catch {
            guard request == ticket else { return }
            films = []
            self.error = "读取失败，请检查网络或修改点播接口。"
        }
    }
}
@main struct AobingApp: App {
    var body: some Scene { WindowGroup { LibraryView().preferredColorScheme(.dark) } }
}
struct LibraryView: View {
    @StateObject private var library = Library()
    @AppStorage("catalogAPI") private var api = "https://api.changzhang.tv/api.php/provide/vod/from/changzhangm3u8/"
    @State private var search = ""
    @State private var settings = false
    var body: some View {
        NavigationStack {
            ScrollView {
                if library.loading { ProgressView().padding() }
                if !library.error.isEmpty { Text(library.error).padding() }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 135))], spacing: 20) {
                    ForEach(library.films) { film in
                        NavigationLink {
                            FilmView(film: film)
                        } label: {
                            VStack(alignment: .leading) {
                                AsyncImage(url: URL(string: film.vod_pic ?? "")) { image in
                                    image.resizable().scaledToFill()
                                } placeholder: {
                                    Rectangle().fill(Color.blue.opacity(0.15))
                                        .overlay(Image(systemName: "film"))
                                }
                                .frame(height: 180).clipped().cornerRadius(12)
                                Text(film.vod_name).lineLimit(2).foregroundStyle(.white)
                            }
                        }
                    }
                }.padding()
            }
            .background(Color(red: 0.035, green: 0.075, blue: 0.14))
            .navigationTitle("敖丙视界")
            .searchable(text: $search, prompt: "搜索电影、剧集")
            .onSubmit(of: .search) { Task { await library.load(api: api, search: search) } }
            .toolbar { Button("设置") { settings = true } }
            .task { await library.load(api: api, search: search) }
            .sheet(isPresented: $settings) {
                NavigationStack {
                    Form {
                        Section("点播接口") {
                            TextField("HTTP / HTTPS JSON 接口", text: $api)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                        }
                        Section("关于") { Text("Rios敖丙 · 0.1.0") }
                    }.navigationTitle("设置")
                        .toolbar { Button("完成") {
                            settings = false
                            Task { await library.load(api: api, search: search) }
                        } }
                }
            }
        }
    }
}
struct FilmView: View {
    let film: Film
    @State private var playback: Playback?
    private var episodes: [Episode] {
        var result: [Episode] = []
        for (line, group) in (film.vod_play_url ?? "").components(separatedBy: "$$$").enumerated() {
            for episode in group.components(separatedBy: "#") {
                let parts = episode.split(separator: "$", maxSplits: 1).map(String.init)
                guard parts.count == 2, let url = URL(string: parts[1]),
                      ["https", "http"].contains(url.scheme ?? "") else { continue }
                result.append(Episode(id: result.count, name: "线路\(line + 1) · \(parts[0])", url: url))
            }
        }
        return result
    }
    var body: some View {
        List {
            if episodes.isEmpty { Text("此条目暂未提供可播放的选集") }
            ForEach(episodes) { episode in
                Button(episode.name) { playback = Playback(url: episode.url) }
            }
        }
        .navigationTitle(film.vod_name)
        .sheet(item: $playback) { item in NativePlayer(url: item.url) }
    }
}
struct NativePlayer: View {
    @State private var player: AVPlayer
    @Environment(\.dismiss) private var dismiss
    init(url: URL) { _player = State(initialValue: AVPlayer(url: url)) }
    var body: some View {
        NavigationStack {
            VideoPlayer(player: player)
                .onAppear { player.play() }
                .onDisappear { player.pause() }
                .toolbar { Button("关闭") { dismiss() } }
        }
    }
}
