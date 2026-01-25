// ...existing code...
using System.Windows.Forms; // Add this for FontDialog (Windows Forms)
// ...existing code...

private void ChooseWindowsFontButton_Click(object sender, RoutedEventArgs e)
{
    var fontDialog = new FontDialog();
    if (fontDialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
    {
        // Example: Apply font to a TextBlock named 'PreviewTextBlock'
        PreviewTextBlock.FontFamily = new System.Windows.Media.FontFamily(fontDialog.Font.Name);
        PreviewTextBlock.FontSize = fontDialog.Font.Size;
        // ...apply other font properties as needed...
    }
}

private void ChooseMacFontButton_Click(object sender, RoutedEventArgs e)
{
    System.Windows.MessageBox.Show("Font selection for Mac is not supported in this version. Please use Windows to select fonts.");
    // For cross-platform, implement a custom font picker or use a cross-platform UI framework.
}
// ...existing code...
