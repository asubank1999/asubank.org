import SwiftUI

/// University halls: type the number on a hall's door and see the building, floor and room,
/// plus the list of building numbers. Replaces the web page /guides/halls (and /guides/colleges_hall).
struct Building: Identifiable {
    let code: String
    let name: String
    let ar: String
    let symbol: String
    var id: String { code }
}

enum Halls {
    static let buildings: [Building] = [
        .init(code: "1", name: "College of IT", ar: "كلية تكنولوجيا المعلومات", symbol: "desktopcomputer"),
        .init(code: "2", name: "College of Law", ar: "كلية الحقوق", symbol: "building.columns"),
        .init(code: "3", name: "Workshops", ar: "الورش", symbol: "wrench.and.screwdriver"),
        .init(code: "4", name: "College of Pharmacy", ar: "كلية الصيدلة", symbol: "pills"),
        .init(code: "5", name: "College of Business", ar: "كلية إدارة الأعمال", symbol: "briefcase"),
        .init(code: "6", name: "Gymnasium", ar: "الصالة الرياضية", symbol: "sportscourt"),
        .init(code: "7", name: "College of Nursing", ar: "كلية التمريض", symbol: "cross.case"),
        .init(code: "8", name: "College of Arts", ar: "كلية الآداب", symbol: "book"),
        .init(code: "9", name: "College of Engineering", ar: "كلية الهندسة", symbol: "gearshape.2"),
        .init(code: "10", name: "Deanship", ar: "العمادة", symbol: "person.text.rectangle"),
        .init(code: "21", name: "College of Dentistry", ar: "كلية طب الأسنان", symbol: "face.smiling"),
        .init(code: "G/B", name: "Clinics Building", ar: "مبنى العيادات", symbol: "stethoscope"),
    ]

    static func building(_ code: String) -> Building? { buildings.first { $0.code == code } }

    struct Decoded {
        let building: String
        let floor: Int
        let room: String
        var place: Building? { Halls.building(building) }
    }

    /// 1206 = building 1, floor 2, room 06. Five digits (10xxx, 21xxx) have a two-digit building.
    static func decode(_ input: String) -> Decoded? {
        let d = input.filter(\.isNumber)
        guard d.count == 4 || d.count == 5 else { return nil }
        let b = d.count == 5 ? 2 : 1
        let chars = Array(d)
        return Decoded(
            building: String(chars[0..<b]).trimmingLeadingZeros,
            floor: Int(String(chars[b])) ?? 0,
            room: String(chars[(b + 1)...])
        )
    }

    static func floorName(_ f: Int) -> String {
        switch f {
        case 0: return "Ground floor"
        case 1: return "1st floor"
        case 2: return "2nd floor"
        case 3: return "3rd floor"
        default: return "\(f)th floor"
        }
    }

    static func floorAr(_ f: Int) -> String {
        let names = ["الطابق الأرضي", "الطابق الأول", "الطابق الثاني", "الطابق الثالث", "الطابق الرابع", "الطابق الخامس", "الطابق السادس", "الطابق السابع", "الطابق الثامن", "الطابق التاسع"]
        return f < names.count ? names[f] : "الطابق \(f)"
    }
}

private extension String {
    var trimmingLeadingZeros: String {
        let t = drop { $0 == "0" }
        return t.isEmpty ? "0" : String(t)
    }
}

private let buildingColor = Color(UIColor(hex: 0x0a84ff))
private let floorColor = Color(UIColor(hex: 0xe8590c))
private let roomColor = Color(UIColor(hex: 0x1f8a3e))

struct HallsScreen: View {
    @State private var number = ""
    @State private var focused: Bool = false

    var body: some View {
        List {
            Section14 {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Find a hall").font(.title3.weight(.bold))
                    Text("Type the number written on the door or in your timetable.")
                        .font(.subheadline).fg14(.secondary)
                    HStack {
                        Image(systemName: "number").fg14(.secondary)
                        TextField("e.g. 1206", text: $number)
                            .keyboardType(.numberPad)
                            .font(.title2.monoDigit14().weight(.semibold))
                            .focused14($focused)
                            .onChange(of: number) { v in
                                let digits = String(v.filter(\.isNumber).prefix(5))
                                if digits != v { number = digits }
                                if Halls.decode(digits) != nil { Haptics.tap() }
                            }
                        if !number.isEmpty {
                            Button { number = "" } label: { Image(systemName: "xmark.circle.fill").fg14(.secondary) }
                                .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(12)
                    .bgIn14(Color.abBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    if let d = Halls.decode(number) {
                        HallResult(d: d, digits: number)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    } else if !number.isEmpty {
                        Text(number.count < 4 ? "Keep typing: hall numbers have 4 digits (5 for buildings 10 and 21)." : "That doesn't look like a hall number.")
                            .font(.footnote).fg14(.secondary)
                    }
                }
                .padding(.vertical, 6)
                .animation(.spring(response: 0.35, dampingFraction: 0.85), value: Halls.decode(number) != nil)
            }

            Section14 {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 2) {
                        Digit(text: "1", color: buildingColor)
                        Digit(text: "2", color: floorColor)
                        Digit(text: "06", color: roomColor)
                    }
                    .frame(maxWidth: .infinity)
                    Legend(color: buildingColor, title: "Building", detail: "1 = College of IT")
                    Legend(color: floorColor, title: "Floor", detail: "2 = 2nd floor")
                    Legend(color: roomColor, title: "Room", detail: "06 = room 06 on that floor")
                    Text("Buildings 10 and 21 use two digits, so their halls have 5 digits: 21105 = Dentistry, 1st floor, room 05.")
                        .font(.footnote).fg14(.secondary)
                    Text("الرقم الأول للمبنى، ثم الطابق، ثم رقم القاعة. مثال: 1206 = كلية تكنولوجيا المعلومات، الطابق الثاني، القاعة 06")
                        .font(.footnote).fg14(.secondary)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .environment(\.layoutDirection, .rightToLeft)
                }
                .padding(.vertical, 6)
            } header: {
                Text("How to read a hall number")
            }

            Section14 {
                ForEach(Halls.buildings) { b in
                    Button {
                        if b.code.allSatisfy(\.isNumber) { number = b.code; focused = true }
                    } label: {
                        HStack(spacing: 12) {
                            Text(b.code)
                                .font(.headline.monoDigit14())
                                .fg14(.white)
                                .frame(minWidth: 34, minHeight: 34)
                                .padding(.horizontal, b.code.count > 2 ? 4 : 0)
                                .bgIn14(buildingColor.abGradient, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(b.name).fg14(.primary)
                                Text(b.ar).font(.footnote).fg14(.secondary)
                            }
                            Spacer()
                            Image(systemName: b.symbol).fg14(.secondary)
                        }
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            } header: {
                Text("Building numbers")
            } footer: {
                Text("Tap a building to start typing one of its halls.")
            }
        }
        .navigationTitle("University halls")
        .navigationBarTitleDisplayMode(.large)
        .dismissKeyboardOnScroll()
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Spacer()
                Button("Done") { focused = false }
            }
        }
    }
}

private struct HallResult: View {
    let d: Halls.Decoded
    let digits: String

    var body: some View {
        let chars = Array(digits)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 2) {
                Digit(text: String(chars[0..<(digits.count == 5 ? 2 : 1)]), color: buildingColor)
                Digit(text: String(chars[digits.count == 5 ? 2 : 1]), color: floorColor)
                Digit(text: d.room, color: roomColor)
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
            ResultRow(color: buildingColor, symbol: d.place?.symbol ?? "questionmark.circle", title: d.place?.name ?? "Unknown building \(d.building)",
                      detail: d.place?.ar ?? "Not in the list of buildings")
            ResultRow(color: floorColor, symbol: "square.stack.3d.up", title: Halls.floorName(d.floor), detail: Halls.floorAr(d.floor))
            ResultRow(color: roomColor, symbol: "number.square", title: "Room \(d.room)", detail: "القاعة \(d.room)")
        }
        .padding(14)
        .bgIn14(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct ResultRow: View {
    let color: Color
    let symbol: String
    let title: String
    let detail: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .fg14(.white)
                .frame(width: 32, height: 32)
                .bgIn14(color.abGradient, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.body.weight(.semibold))
                Text(detail).font(.footnote).fg14(.secondary)
            }
        }
    }
}

private struct Digit: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(.system(size: 34, weight: .bold, design: .rounded).monoDigit14())
            .fg14(color)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .bgIn14(color.opacity(0.13), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct Legend: View {
    let color: Color
    let title: String
    let detail: String
    var body: some View {
        HStack(spacing: 10) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(title).font(.subheadline.weight(.semibold))
            Text(detail).font(.subheadline).fg14(.secondary)
        }
    }
}
