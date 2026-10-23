# Changelog

All notable changes to Arch-SME. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- A translation sheet: `tools/translation_sheet.py` exports every string to a spreadsheet, with where it appears and the marks to keep, and imports the translator's sheet back.
- README with screenshots, and the license texts of the bundled fonts.
- The code is released under the MIT License.

## [1.0.0] - not released yet

The first complete version, for one shop on an Android tablet or phone, fully offline.

### Added
- **Selling:**
  - Product grid with categories and search, and a cart.
  - Payment in cash, Mobile Money, or credit to a named customer.
  - Quick cash amounts and change.
  - A 6-second Undo after each sale.
  - Today's sales, sale details, and voids with a reason.
- **Stock:**
  - Stock list with low-stock warnings.
  - Add purchase, paid now or not paid yet, with "paid from" cash or Mobile Money.
  - New and edited products, with a starting stock.
  - Stock corrections.
  - Stock always equals the sum of its recorded movements.
- **Suppliers:** list and detail, product links, a suggested reorder quantity per product, and a WhatsApp order message.
- **The day's money:**
  - Open the day with a note counter. Selling, purchases and expenses stay locked until the day is open.
  - Record money: expenses, credit paid back, cash in, cash out.
  - Owed to the shop: each customer's credit balance.
  - Close the day in three steps (check, count, close), with a reason when the drawer is short or over, and an optional WhatsApp summary.
  - Reopen a closed day, and a prompt when yesterday is still open.
  - A close-day reminder notification.
  - Past days are read-only.
- **Reports:** sales, profit, number of sales and average sale; a daily chart; how customers paid; top products. Today, this week, this month or a custom range, plus a per-day tab. Export to PDF and CSV.
- **Safety:**
  - An optional PIN for voids, stock corrections, closing, reopening and restoring. Five wrong tries lock it for 30 seconds.
  - Backup to a single zip, and a restore that is checked and staged before it is applied.
  - An automatic backup after each closed day; the last 7 are kept.
- **First run:** onboarding that sets up the shop and opens the first day, and product import from CSV.
- **Layouts:**
  - Tablet landscape with a rail and two panes, tablet portrait with the rail, phone with a bottom bar.
  - Light and dark themes.
  - Layouts hold at 200% font size.
- **Languages:** English, Kiswahili and Portuguese; drafts of Luganda and Tigrinya. Amounts always use Latin digits with commas.

### Changed
- The app is named Arch-SME, with a new adaptive launcher icon.
- Fonts (Bricolage Grotesque, DM Sans, Noto Sans Ethiopic) ship inside the app, so they show without Google Play services or a network.
- **Close the day:**
  - Banknotes are shown in their own colours.
  - The counted total rises as you count, with a progress bar.
  - A clear band reads Balanced, or Short / Over with the reasons to pick from.
  - While you are still counting, a short drawer reads "still to count" rather than a warning. The reason is asked for when you tap Close the day.
- One label, "Close the day", wherever the day is closed.
- Rust is used only where it means something: low stock, and money owed above zero.
- Low stock is marked with an icon as well as colour.
- Input borders, switches and the chart's lighter bars have stronger contrast.

### Fixed
- In-app language switching on Android 12 and older.
- **Tab navigation:**
  - The cart survives switching tabs.
  - Suppliers works again after recording a purchase.
  - Phones reach the Day screen from the Home day bar.
- **At large font sizes:**
  - Buttons stack instead of splitting words.
  - Bottom-bar labels share one size that fits.
  - Card links wrap under their titles.
- Backups record their own time.

[Unreleased]: https://github.com/nhmdnl/shop-manager/compare/master...develop
[1.0.0]: https://github.com/nhmdnl/shop-manager/tree/master
