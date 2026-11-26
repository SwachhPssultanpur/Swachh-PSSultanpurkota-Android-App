Swachh Sultanpur Android APK Project

इस संशोधित संस्करण में Android WebView के Camera और GPS permission handling में सुधार किया गया है।

मुख्य बदलाव:
1. HTML file input में capture="environment" होने पर Android अब Gallery chooser के बजाय सीधे Camera intent खोलता है।
2. सामान्य image file input पर Gallery picker खुलता है।
3. WebView Geolocation request आने पर ही Android Location permission माँगी जाती है; ऐप खुलते ही permission dialog से टकराव नहीं होगा।
4. Apps Script Web App URL और मौजूदा Google Sheets/Drive backend नहीं बदले गए हैं।

महत्वपूर्ण:
- Apps Script Code.gs / Index.html को इस Android project में बदलने की जरूरत नहीं है। APK उसी deployed Apps Script URL को खोलता है।
- वास्तविक फोन पर Camera और GPS का परीक्षण अभी आवश्यक है। अलग-अलग Android/WebView और फोन निर्माता के अनुसार व्यवहार बदल सकता है।
- GPS के लिए फोन की Location ON, ऐप की Location permission Allow और इंटरनेट चालू रखें।
- Camera button के HTML input में capture="environment" होना चाहिए।

GitHub पर अपलोड:
1. ZIP Extract करें।
2. Extract किए गए मुख्य folder के अंदर की सभी files/folders Repository root पर अपलोड करें (अंदर का parent folder अपलोड न करें)।
3. समान paths पर files replace करें।
4. Actions में Build Swachh Sultanpur APK चलाएँ।
5. Build सफल होने पर artifact से app-debug.apk डाउनलोड करें।
