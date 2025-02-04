import React, { useState, useEffect } from 'react'
import { Sun, Moon } from 'lucide-react'
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import Link from 'next/link'
import Image from 'next/image'

export default function ASULibrary() {
  const [darkMode, setDarkMode] = useState(false)

  useEffect(() => {
    if (localStorage.getItem('theme') === 'dark' || 
        (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      setDarkMode(true)
    }
  }, [])

  useEffect(() => {
    document.body.classList.toggle('dark', darkMode)
    localStorage.setItem('theme', darkMode ? 'dark' : 'light')
  }, [darkMode])

  const toggleDarkMode = () => setDarkMode(!darkMode)

  const subjects = [
    { name: 'ALL | RECENT', href: '/subjects', icon: '/new.gif' },
    { name: 'Study Plans الخطة الدراسية', href: 'https://drive.google.com/drive/folders/1QNvzQm4TjBg4x-dRpFp9roUBxdu2dQRY?usp=sharing' },
    { name: 'University Calendar 2025 تقويم الجامعة', href: 'https://drive.google.com/file/d/1wfPIE57s_B-tkasGEmlyRx6yJX31czBi/view?usp=sharing' },
    { name: 'PLACEMENT TESTS امتحانات المستوى', href: '/placement' },
    { name: 'PLACEMENT RESULTS نتائج المستوى (رقمك الجامعي)', href: 'https://www.asu.edu.jo/ar/Students-Portal/Pages/PlacementResults.aspx' },
    { name: 'LABS MATERIALS مواد المختبرات', href: '/lab_related' },
    { name: 'MOST WANTED FILES الملفات الأكثر طلبا', href: '/mostwanted' },
    { name: 'INFORMATION TECHNOLOGY', href: '/it' },
    { name: 'ENGINEERING الهندسة', href: '/engineering' },
    { name: 'REQUESTED DATA (ارفع ملفك)', href: '/special' },
    { name: 'ELECTIVE COURSES / OTHER مواد اختياري او متطلب', href: '/others' },
    { name: 'PRESENTATIONS', href: '/presentations' },
    { name: 'SUMMARIES الملخصات', href: '/summaries' },
    { name: 'ALL CALCULUS', href: '/calculus' },
    { name: 'HEALTH SCIENCES', href: '/medical' },
    { name: 'MATHEMATICS AND STATISTICS', href: '/mathematics' },
    { name: 'ARTS AND HUMANITIES', href: '/artshumanities' },
    { name: 'CHEMICAL ENGINEERING', href: '/chemical' },
    { name: 'BUSINESS', href: '/business' },
    { name: 'ELECTRICAL ENGINEERING', href: '/electrical' },
    { name: 'INDUSTRIAL ENGINEERING', href: '/industrial' },
    { name: 'DENTISTRY', href: '/dentistry' },
    { name: 'NURSING', href: '/nursing' },
    { name: 'PHARMACY', href: '/pharmacy' },
    { name: 'SHARIAH الشريعة', href: '/hanafi' },
    { name: 'ARABIC LITERATURE الأدب العربي', href: '/arabicliterature' },
    { name: 'Electronic exams for common intermediate diploma subjects (mid-term exams) امتحانات إلكترونية لمواد الدبلوم المتوسط المشتركة (امتحانات منتصف الفصل )', href: '/placement' },
    { name: 'ASU 2025 LIBRARY', href: '/modern_index_passworded' },
    { name: 'ASU 2025 LIBRARY v2.0', href: '/modern_index_passworded2' },
  ]

  return (
    <div className={`min-h-screen ${darkMode ? 'bg-gray-900 text-gray-100' : 'bg-green-50 text-gray-900'}`}>
      <header className="text-center py-8">
        <div className="banner mb-4">
          <div className="banner-content flex justify-center items-center">
            <Image src="/ASU_Jordan_logo-removebg-preview.png" alt="ASU Logo" width={100} height={100} className="mr-4" />
            <h1 className="text-4xl font-bold">𝔸𝕊𝕌 𝕃𝕚𝕓𝕣𝕒𝕣𝕪</h1>
          </div>
        </div>
        <div className="message text-sm mb-4">
          <p>Admin: necro144@yahoo.com</p>
          <p>This library can be accessed through: https://asubank.xyz</p>
          <p>The site is currently under maintenance</p>
        </div>
        <Button asChild className="mb-4">
          <a href="https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ" target="_blank" rel="noopener noreferrer">
            WhatsApp
          </a>
        </Button>
        <nav>
          <ul className="flex justify-center space-x-4">
            <li><Link href="/allinoneexplained" className="hover:underline">LIBRARY</Link></li>
            <li><Link href="/modernindex" className="hover:underline">ASU-BANK v2</Link></li>
            <li><Link href="/subjects" className="hover:underline">RECENT</Link></li>
            <li>
              <Button onClick={toggleDarkMode} variant="outline" size="icon">
                {darkMode ? <Sun className="h-[1.2rem] w-[1.2rem]" /> : <Moon className="h-[1.2rem] w-[1.2rem]" />}
                <span className="sr-only">Toggle theme</span>
              </Button>
            </li>
          </ul>
        </nav>
      </header>
      <main className="container mx-auto px-4">
        <div className="text-center mb-8 animate-pulse">
          <Link href="https://drive.google.com/file/d/1wfPIE57s_B-tkasGEmlyRx6yJX31czBi/view?usp=sharing" target="_blank" rel="noopener noreferrer" className="text-cyan-500 hover:underline mr-4">
            التقويم الجامعي 2025/2024
          </Link>
          <Link href="https://www.asu.edu.jo/ar/Students-Portal/Pages/PlacementResults.aspx" target="_blank" rel="noopener noreferrer" className="text-red-500 hover:underline">
            نتائج المستوى (رقمك الجامعي)
          </Link>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {subjects.map((subject, index) => (
            <Card key={index} className={`p-6 ${darkMode ? 'bg-gray-800 hover:bg-gray-700' : 'bg-white hover:bg-gray-100'} transition-colors duration-200`}>
              <Link href={subject.href} className="block text-center">
                <h3 className="text-lg font-semibold flex items-center justify-center">
                  {subject.icon && <Image src={subject.icon} alt="" width={20} height={10} className="mr-2" />}
                  {subject.name}
                </h3>
              </Link>
            </Card>
          ))}
        </div>
      </main>
    </div>
  )
}