document.getElementById('mode-toggle').addEventListener('click', () => {
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('theme', document.body.classList.contains('dark-mode') ? 'dark' : 'light');
});

window.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('theme') === 'dark' || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        document.body.classList.add('dark-mode');
    }
});

function copyText() {
    const copyText = document.getElementById("textToCopy");
    copyText.select();
    copyText.setSelectionRange(0, 99999);
    navigator.clipboard.writeText(copyText.value).then(() => {
        document.getElementById("message").textContent = "You have my CliQ number! Any amount is appreciated - Name starts with 'qusai'";
    }).catch(err => {
        console.error('Could not copy text: ', err);
        document.getElementById("message").textContent = "Failed to copy text.";
    });
}
