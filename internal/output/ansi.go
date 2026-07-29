package output

const (
	colorReset  = "\033[0m"
	colorCyan   = "\033[36m"
	colorGreen  = "\033[32m"
	colorPurple = "\033[35m"
	colorRed    = "\033[31m"
	colorWhite  = "\033[37m"
)

func colorize(text, color string) string {
	return color + text + colorReset
}

func Cyan(text string) string   { return colorize(text, colorCyan) }
func GreenBold(text string) string { return colorize(text, "\033[1;32m") }
func RedBold(text string) string   { return colorize(text, "\033[1;31m") }
func PurpleBold(text string) string { return colorize(text, "\033[1;35m") }
func White(text string) string    { return colorize(text, colorWhite) }
func WhiteBold(text string) string { return colorize(text, "\033[1;37m") }
